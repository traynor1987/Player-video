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
data class XtreamStream(
    @Json(name = "stream_id") val id: Int,
    val name: String,
    @Json(name = "stream_icon") val icon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "epg_channel_id") val epgId: String? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "container_extension") val extension: String? = null
)
@JsonClass(generateAdapter = true)
data class XtreamSeries(@Json(name = "series_id") val id: Int, val name: String, @Json(name = "cover") val cover: String? = null, @Json(name = "category_id") val categoryId: String? = null)

interface XtreamApi {
    @GET suspend fun authenticate(@Url url: String, @Query("username") username: String, @Query("password") password: String): Response<XtreamAuthResponse>
    @GET suspend fun categories(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String): Response<List<XtreamCategory>>
    @GET suspend fun streams(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String): Response<List<XtreamStream>>
    @GET suspend fun series(@Url url: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_series"): Response<List<XtreamSeries>>
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
        return java.net.URI(uri.scheme, null, uri.host, uri.port, path.ifBlank { null }, null, null).toString().trimEnd('/')
    }
    fun api(server: String) = "${normaliseServer(server)}/player_api.php"
    fun live(server: String, user: String, password: String, id: Int, extension: String? = null): String =
        "${normaliseServer(server)}/live/${encode(user)}/${encode(password)}/$id.${extension ?: "ts"}"
    fun movie(server: String, user: String, password: String, id: Int, extension: String?): String =
        "${normaliseServer(server)}/movie/${encode(user)}/${encode(password)}/$id.${extension ?: "mp4"}"
    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
