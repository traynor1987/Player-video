package com.traynor.player.data.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class VerifiedUpdate(val update: AvailableUpdate, val file: File, val sha256: String)

class ApkUpdateInstaller(private val context: Context, private val client: OkHttpClient) {
    suspend fun downloadAndVerify(update: AvailableUpdate, onProgress: (Int) -> Unit): VerifiedUpdate = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val destination = File(directory, update.asset.name)
        val request = Request.Builder().url(update.asset.downloadUrl).header("Accept", "application/vnd.android.package-archive").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (${response.code})")
            val body = response.body ?: error("Update download was empty")
            val length = body.contentLength()
            body.byteStream().use { input -> FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var read: Int; var total = 0L
                while (input.read(buffer).also { read = it } >= 0) {
                    output.write(buffer, 0, read); total += read
                    if (length > 0) onProgress(((total * 100) / length).toInt().coerceIn(0, 100))
                }
            } }
        }
        if (destination.length() < 4 || destination.inputStream().use { it.read() != 0x50 || it.read() != 0x4b }) error("Downloaded file is not an APK")
        val sha = destination.sha256()
        update.checksum?.let { checksumAsset ->
            val expected = downloadText(checksumAsset.downloadUrl).trim().substringBefore(' ').removePrefix("sha256:").lowercase()
            if (!expected.matches(Regex("[0-9a-f]{64}")) || expected != sha) error("Update checksum verification failed")
        }
        val archive = packageInfo(destination) ?: error("Downloaded file is not a valid Android package")
        if (archive.packageName != context.packageName) error("Update package does not match this app")
        if (archive.versionCodeLong() <= installedPackage().versionCodeLong()) error("Downloaded update is not newer than the installed app")
        if (!sameSigner(archive, installedPackage())) error("Update signing certificate does not match the installed app")
        VerifiedUpdate(update, destination, sha)
    }

    fun openInstaller(verified: VerifiedUpdate): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return InstallLaunchResult.PermissionRequired
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", verified.file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return InstallLaunchResult.InstallerOpened
    }

    private fun downloadText(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Could not download update checksum")
        response.body?.string() ?: error("Update checksum was empty")
    }
    private fun packageInfo(file: File): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())) else @Suppress("DEPRECATION") context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
    private fun installedPackage(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())) else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private fun sameSigner(archive: PackageInfo, installed: PackageInfo): Boolean {
        val archiveSigners = signingCertificates(archive)
        val installedSigners = signingCertificates(installed)
        return archiveSigners.isNotEmpty() && archiveSigners.any { candidate -> installedSigners.any { candidate.contentEquals(it) } }
    }
    private fun signingCertificates(info: PackageInfo): List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
    } else legacyCertificates(info)
    @Suppress("DEPRECATION") private fun legacyCertificates(info: PackageInfo): List<ByteArray> = info.signatures.orEmpty().map { it.toByteArray() }
    private fun PackageInfo.versionCodeLong(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var read: Int
        while (input.read(buffer).also { read = it } >= 0) digest.update(buffer, 0, read)
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

enum class InstallLaunchResult { PermissionRequired, InstallerOpened }
