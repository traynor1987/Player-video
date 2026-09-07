package com.traynor.player.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(@Json(name = "user_info") val userInfo: XtreamUserInfo? = null)
@JsonClass(generateAdapter = true)
data class XtreamUserInfo(val username: String? = null, val status: String? = null, @Json(name = "auth") val authenticated: String? = null)
@JsonClass(generateAdapter = true)
data class XtreamCategory(@Json(name = "category_id") val id: String, @Json(name = "category_name") val name: String)
@JsonClass(generateAdapter = true)
data class XtreamVodStream(
    @Json(name = "stream_id") val id: Int,
    val name: String,
    @Json(name = "stream_icon") val icon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "container_extension") val extension: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val plot: String? = null,
    val duration: String? = null
)
@JsonClass(generateAdapter = true)
data class XtreamVodInfoResponse(val info: XtreamVodInfo? = null)
@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    val plot: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val year: String? = null,
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "tmdb_id") val tmdbId: String? = null
)
@JsonClass(generateAdapter = true)
data class XtreamStream(
    @Json(name = "stream_id") val id: Int,
    val name: String,
    @Json(name = "stream_icon") val icon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "epg_channel_id") val epgId: String? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "container_extension") val extension: String? = null,
    // Some legitimate Xtream-compatible services expose a stream-specific URL.
    // Prefer it when supplied instead of reconstructing a generic endpoint.
    @Json(name = "direct_source") val directSource: String? = null
)
@JsonClass(generateAdapter = true)
data class XtreamSeries(
    @Json(name = "series_id") val id: Int,
    val name: String,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    val plot: String? = null,
    val rating: String? = null,
    val year: String? = null
)
@JsonClass(generateAdapter = true)
data class XtreamSeriesInfo(val episodes: Map<String, List<XtreamEpisode>>? = null)
@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    val id: String? = null,
    val title: String? = null,
    @Json(name = "episode_num") val number: Int? = null,
    @Json(name = "container_extension") val extension: String? = null,
    val info: XtreamEpisodeInfo? = null
)
@JsonClass(generateAdapter = true)
data class XtreamEpisodeInfo(val plot: String? = null, val duration: String? = null, @Json(name = "movie_image") val image: String? = null)

interface XtreamApi {
    @GET suspend fun authenticate(@Url url: String, @Query("username") username: String, @Query("password") password: String): Response<XtreamAuthResponse>
    @GET suspend fun categories(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String): Response<List<XtreamCategory>>
    @GET suspend fun streams(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String): Response<List<XtreamStream>>
    @GET suspend fun vodStreams(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_vod_streams"): Response<List<XtreamVodStream>>
    @GET suspend fun vodInfo(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_vod_info", @Query("vod_id") vodId: String): Response<XtreamVodInfoResponse>
    @GET suspend fun series(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_series"): Response<List<XtreamSeries>>
    @GET suspend fun seriesInfo(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_series_info", @Query("series_id") seriesId: String): Response<XtreamSeriesInfo>
}

object XtreamUrls {
    fun normaliseServer(raw: String): String {
        val value = raw.trim()
        require(value.isNotBlank()) { "Server URL is required" }
        val withScheme = if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
        val uri = java.net.URI(withScheme)
        require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter a valid server URL without embedded credentials" }
        // Accept either the bare server URL or a full player_api.php URL, but
        // never retain pasted query parameters or embedded credentials.
        val path = uri.path.orEmpty().trimEnd('/').replace(Regex("/(player_api|get|xmltv)\\.php$", RegexOption.IGNORE_CASE), "")
        val canonicalPath = path.ifBlank { "" }.let { if (it.isEmpty()) null else it }
        return java.net.URI(uri.scheme, null, uri.host, uri.port, canonicalPath, null, null).toString().trimEnd('/')
    }
    fun api(server: String) = "${normaliseServer(server)}/player_api.php"
    fun live(server: String, user: String, password: String, id: Int, extension: String? = null): String =
        "${normaliseServer(server)}/live/${encode(user)}/${encode(password)}/$id.${extension ?: "ts"}"
    fun movie(server: String, user: String, password: String, id: Int, extension: String?): String =
        "${normaliseServer(server)}/movie/${encode(user)}/${encode(password)}/$id.${extension ?: "mp4"}"
    fun episode(server: String, user: String, password: String, id: String, extension: String?): String =
        "${normaliseServer(server)}/series/${encode(user)}/${encode(password)}/${encode(id)}.${extension ?: "mp4"}"
    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
