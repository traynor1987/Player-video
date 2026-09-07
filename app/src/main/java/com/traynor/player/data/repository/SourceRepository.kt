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

data class SeriesEpisode(
    val seriesId: Long,
    val episodeId: String,
    val season: String,
    val number: Int?,
    val title: String,
    val extension: String?,
    val description: String? = null,
    val artworkUrl: String? = null
)
data class MovieDetails(
    val synopsis: String? = null,
    val runtime: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null
)

class SourceRepository(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
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
                        response.body()?.userInfo?.let { it.authenticated == "1" || it.status.equals("Active", true) } == true -> ConnectionResult.Success(response.body()?.userInfo?.username)
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
        movieDao.deleteForSource(sourceId)
        seriesDao.deleteForSource(sourceId)
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
                streamUrlEncrypted = cipher.encrypt(item.directSource.asPlayableHttpUrl()
                    ?: XtreamUrls.live(server, user, pass, item.id, item.extension)), logoUrl = item.icon,
                category = categories[item.categoryId] ?: "Uncategorised", tvgId = item.epgId, searchText = item.name.lowercase()) })
            emit(ImportProgress((index + 1) * 500.coerceAtMost(streams.size), "Importing Live TV"))
        }
        emit(ImportProgress(streams.size, "Imported ${streams.size} live channels"))
        importMovies(source, endpoint, server, user, pass)
        importSeries(source, endpoint, user, pass)
        emit(ImportProgress(streams.size, "Source library is ready", true))
    }

    private suspend fun FlowCollector<ImportProgress>.importMovies(source: SourceEntity, endpoint: String, server: String, user: String, pass: String) {
        val categories = api.categories(endpoint, user, pass, "get_vod_categories").body().orEmpty().associate { it.id to it.name }
        val movies = api.vodStreams(endpoint, user, pass).body().orEmpty()
        movies.chunked(500).forEachIndexed { index, batch ->
            movieDao.upsertAll(batch.map { item -> MovieEntity(
                sourceId = source.id, externalId = item.id.toString(), title = item.name,
                streamUrlEncrypted = cipher.encrypt(XtreamUrls.movie(server, user, pass, item.id, item.extension)),
                posterUrl = item.icon, category = categories[item.categoryId] ?: "Uncategorised",
                description = item.plot, year = item.year, rating = item.rating, runtime = item.duration,
                searchText = item.name.lowercase()
            ) })
            emit(ImportProgress((index + 1) * 500, "Importing movies"))
        }
        emit(ImportProgress(movies.size, "Imported ${movies.size} movies"))
    }

    private suspend fun FlowCollector<ImportProgress>.importSeries(source: SourceEntity, endpoint: String, user: String, pass: String) {
        val categories = api.categories(endpoint, user, pass, "get_series_categories").body().orEmpty().associate { it.id to it.name }
        val shows = api.series(endpoint, user, pass).body().orEmpty()
        shows.chunked(500).forEachIndexed { index, batch ->
            seriesDao.upsertAll(batch.map { item -> SeriesEntity(
                sourceId = source.id, externalId = item.id.toString(), title = item.name, posterUrl = item.cover,
                category = categories[item.categoryId] ?: "Uncategorised", description = item.plot,
                year = item.year, rating = item.rating, searchText = item.name.lowercase()
            ) })
            emit(ImportProgress((index + 1) * 500, "Importing series"))
        }
        emit(ImportProgress(shows.size, "Imported ${shows.size} series"))
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

    suspend fun playableUrls(channelId: Long): List<String> = channelDao.get(channelId)?.let {
        streamCandidates(cipher.decrypt(it.streamUrlEncrypted))
    }.orEmpty()

    suspend fun playableMovieUrls(movieId: Long): List<String> = movieDao.get(movieId)?.let {
        streamCandidates(cipher.decrypt(it.streamUrlEncrypted))
    }.orEmpty()

    suspend fun movieDetails(movieId: Long): MovieDetails? = withContext(Dispatchers.IO) {
        val movie = movieDao.get(movieId) ?: return@withContext null
        val source = sourceDao.get(movie.sourceId) ?: return@withContext null
        if (source.type != SourceType.XTREAM) return@withContext MovieDetails(movie.description, movie.runtime, movie.rating, movie.year)
        val response = api.vodInfo(
            XtreamUrls.api(cipher.decrypt(source.endpointEncrypted)), cipher.decrypt(source.usernameEncrypted),
            cipher.decrypt(source.passwordEncrypted), vodId = movie.externalId
        )
        val info = response.body()?.info
        MovieDetails(info?.plot ?: movie.description, info?.duration ?: movie.runtime, info?.rating ?: movie.rating, info?.year ?: movie.year, info?.imdbId, info?.tmdbId)
    }

    suspend fun seriesEpisodes(seriesId: Long): List<SeriesEpisode> = withContext(Dispatchers.IO) {
        val series = seriesDao.get(seriesId) ?: return@withContext emptyList()
        val source = sourceDao.get(series.sourceId) ?: return@withContext emptyList()
        if (source.type != SourceType.XTREAM) return@withContext emptyList()
        val endpoint = XtreamUrls.api(cipher.decrypt(source.endpointEncrypted))
        val response = api.seriesInfo(endpoint, cipher.decrypt(source.usernameEncrypted), cipher.decrypt(source.passwordEncrypted), seriesId = series.externalId)
        response.body()?.episodes.orEmpty().flatMap { (season, episodes) -> episodes.mapNotNull { episode ->
            episode.id?.takeIf { it.isNotBlank() }?.let { id -> SeriesEpisode(seriesId, id, season, episode.number, episode.title ?: "Episode", episode.extension, episode.info?.plot, episode.info?.image) }
        } }.sortedWith(compareBy<SeriesEpisode> { it.season.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number ?: Int.MAX_VALUE })
    }

    suspend fun playableEpisodeUrls(seriesId: Long, episodeId: String, extension: String?): List<String> = withContext(Dispatchers.IO) {
        val series = seriesDao.get(seriesId) ?: return@withContext emptyList()
        val source = sourceDao.get(series.sourceId) ?: return@withContext emptyList()
        if (source.type != SourceType.XTREAM) return@withContext emptyList()
        listOf(XtreamUrls.episode(cipher.decrypt(source.endpointEncrypted), cipher.decrypt(source.usernameEncrypted), cipher.decrypt(source.passwordEncrypted), episodeId, extension))
    }

    private fun String?.asPlayableHttpUrl(): String? = this?.trim()?.takeIf {
        runCatching {
            val scheme = java.net.URI(it).scheme
            scheme.equals("http", true) || scheme.equals("https", true)
        }.getOrDefault(false)
    }

    /**
     * Xtream panels occasionally advertise .ts while serving the same live path
     * as HLS, or the reverse. Retrying the matching alternate is safe and avoids
     * any stream discovery: it only reuses the user's own authorised endpoint.
     */
    private fun streamCandidates(primary: String): List<String> {
        val path = primary.substringBefore('?')
        val query = primary.removePrefix(path)
        val alternate = when {
            path.endsWith(".ts", ignoreCase = true) -> path.dropLast(3) + ".m3u8" + query
            path.endsWith(".m3u8", ignoreCase = true) -> path.dropLast(5) + ".ts" + query
            else -> null
        }
        return listOfNotNull(primary, alternate).distinct()
    }
}
