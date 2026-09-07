package com.traynor.player.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.traynor.player.core.model.*
import com.traynor.player.data.local.*
import com.traynor.player.data.network.*
import com.traynor.player.data.parser.M3uParser
import com.traynor.player.security.CredentialCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SourceRepository(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val cipher: CredentialCipher,
    private val api: XtreamApi,
    private val client: OkHttpClient,
    private val resolver: ContentResolver,
    private val parser: M3uParser
) {
    fun sources() = sourceDao.observeAll()

    suspend fun test(draft: SourceDraft): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            when (draft.type) {
                SourceType.XTREAM -> {
                    if (draft.username.isBlank() || draft.password.isBlank()) return@withContext ConnectionResult.InvalidDetails("Username and password are required")
                    val server = XtreamUrls.normaliseServer(draft.serverUrl)
                    val response = api.authenticate(XtreamUrls.api(server), draft.username.trim(), draft.password)
                    when {
                        response.code() == 401 || response.code() == 403 -> ConnectionResult.AuthenticationFailed
                        !response.isSuccessful -> ConnectionResult.ServerUnavailable
                        response.body()?.userInfo?.let { it.authenticated == 1 || it.status.equals("Active", true) } == true -> ConnectionResult.Success(response.body()?.userInfo?.username)
                        else -> ConnectionResult.AuthenticationFailed
                    }
                }
                SourceType.REMOTE_M3U -> {
                    val request = Request.Builder().url(draft.playlistUrl.trim()).get().build()
                    client.newCall(request).execute().use { if (it.isSuccessful && it.body != null) ConnectionResult.Success() else ConnectionResult.ServerUnavailable }
                }
                SourceType.LOCAL_M3U -> resolver.openInputStream(Uri.parse(draft.localUri))?.use { ConnectionResult.Success() }
                    ?: ConnectionResult.InvalidDetails("The selected file cannot be read")
            }
        } catch (e: IllegalArgumentException) { ConnectionResult.InvalidDetails(e.message ?: "Invalid details") }
        catch (_: IOException) { ConnectionResult.ServerUnavailable }
        catch (_: SecurityException) { ConnectionResult.InvalidDetails("Permission to read the playlist was lost") }
    }

    fun addAndImport(draft: SourceDraft): Flow<ImportProgress> = flow {
        val endpoint = when (draft.type) {
            SourceType.XTREAM -> XtreamUrls.normaliseServer(draft.serverUrl)
            SourceType.REMOTE_M3U -> draft.playlistUrl.trim()
            SourceType.LOCAL_M3U -> draft.localUri
        }
        val sourceId = sourceDao.insert(SourceEntity(name = draft.name.trim(), type = draft.type,
            endpointEncrypted = cipher.encrypt(endpoint), usernameEncrypted = cipher.encrypt(draft.username.trim()), passwordEncrypted = cipher.encrypt(draft.password)))
        emitAll(refresh(sourceId).onCompletion { emit(ImportProgress(sourceId.toInt(), "Source ready", true)) })
    }.flowOn(Dispatchers.IO)

    fun refresh(sourceId: Long): Flow<ImportProgress> = flow {
        val source = sourceDao.get(sourceId) ?: error("Source no longer exists")
        channelDao.deleteForSource(sourceId)
        when (source.type) {
            SourceType.XTREAM -> importXtream(source)
            SourceType.REMOTE_M3U, SourceType.LOCAL_M3U -> emitAll(importM3u(source))
        }
        sourceDao.markRefreshed(sourceId, System.currentTimeMillis())
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ImportProgress>.importXtream(source: SourceEntity) {
        val server = cipher.decrypt(source.endpointEncrypted); val user = cipher.decrypt(source.usernameEncrypted); val pass = cipher.decrypt(source.passwordEncrypted)
        val endpoint = XtreamUrls.api(server)
        val categories = api.categories(endpoint, user, pass, "get_live_categories").body().orEmpty().associate { it.id to it.name }
        val streams = api.streams(endpoint, user, pass, "get_live_streams").body() ?: error("Live TV library unavailable")
        streams.chunked(500).forEachIndexed { index, chunk ->
            channelDao.upsertAll(chunk.map { item -> ChannelEntity(sourceId = source.id, externalId = item.id.toString(), name = item.name,
                streamUrlEncrypted = cipher.encrypt(XtreamUrls.live(server, user, pass, item.id, item.extension)), logoUrl = item.icon,
                category = categories[item.categoryId] ?: "Uncategorised", tvgId = item.epgId, searchText = item.name.lowercase()) })
            emit(ImportProgress((index + 1) * 500.coerceAtMost(streams.size), "Importing Live TV"))
        }
        emit(ImportProgress(streams.size, "Imported ${streams.size} live channels", true))
    }

    private fun importM3u(source: SourceEntity): Flow<ImportProgress> {
        val endpoint = cipher.decrypt(source.endpointEncrypted)
        val input: java.io.InputStream = (if (source.type == SourceType.LOCAL_M3U) resolver.openInputStream(Uri.parse(endpoint))
            else client.newCall(Request.Builder().url(endpoint).build()).execute().let { response ->
                if (!response.isSuccessful) { response.close(); throw IOException("Playlist unavailable") }
                response.body?.byteStream()
            }
        ) ?: error("Playlist unavailable")
        return parser.parse(input) { batch -> channelDao.upsertAll(batch.mapIndexed { i, entry -> ChannelEntity(
            sourceId = source.id, externalId = "${entry.tvgId.orEmpty()}:${entry.url.hashCode()}:$i", name = entry.name,
            streamUrlEncrypted = cipher.encrypt(entry.url), logoUrl = entry.logo, category = entry.group ?: "Uncategorised",
            tvgId = entry.tvgId, searchText = "${entry.name} ${entry.tvgName.orEmpty()}".lowercase()) }) }
    }

    suspend fun playableUrl(channelId: Long): String? = channelDao.get(channelId)?.let { cipher.decrypt(it.streamUrlEncrypted) }
}
