package com.traynor.player.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import java.io.IOException

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)
@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    val digest: String? = null,
    val size: Long = 0
)

private interface GitHubReleasesApi { @retrofit2.http.GET("repos/traynor1987/Player-video/releases/latest") suspend fun latest(): GitHubRelease }

data class AvailableUpdate(
    val version: String,
    val asset: GitHubReleaseAsset,
    val checksum: GitHubReleaseAsset? = null,
    val notes: String? = null,
    val releaseUrl: String
)
data class ReleaseCheck(val latestVersion: String, val releaseUrl: String, val update: AvailableUpdate?)
class ReleasePreparingException : IOException("The latest release is still being prepared")

class GitHubReleaseRepository(client: OkHttpClient) {
    private val api = Retrofit.Builder().baseUrl("https://api.github.com/")
        .client(client.newBuilder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Accept", "application/vnd.github+json").header("User-Agent", "Player-Android").build()) }.build())
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build())).build().create(GitHubReleasesApi::class.java)

    suspend fun check(currentVersion: String): ReleaseCheck = withContext(Dispatchers.IO) {
        // GitHub can briefly expose a newly-created release before Actions has attached its APK.
        // Retry transient failures so a healthy connection does not look like a broken updater.
        val release = retryLatest()
        val version = release.tagName.removePrefix("v")
        require(version.matches(Regex("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?"))) { "Latest release has an invalid version" }
        val apkName = "Player-v$version.apk"
        val apk = release.assets.firstOrNull { it.name == apkName }
            ?: throw ReleasePreparingException()
        val checksum = release.assets.firstOrNull { it.name == "$apkName.sha256" }
        val update = if (compareVersions(version, currentVersion) > 0) {
            AvailableUpdate(version, apk, checksum, release.body?.takeIf { it.isNotBlank() }, release.htmlUrl)
        } else null
        ReleaseCheck(version, release.htmlUrl, update)
    }

    private suspend fun retryLatest(): GitHubRelease {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try { return api.latest() } catch (error: Throwable) {
                last = error
                if (attempt < 2) delay(750L * (attempt + 1))
            }
        }
        throw (last ?: IOException("GitHub update check failed"))
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        return (0 until maxOf(a.size, b.size)).map { (a.getOrElse(it) { 0 }).compareTo(b.getOrElse(it) { 0 }) }.firstOrNull { it != 0 } ?: 0
    }
}
