package com.traynor.player.core.model

enum class SourceType { XTREAM, REMOTE_M3U, LOCAL_M3U }
enum class ContentType { LIVE, MOVIE, SERIES }

data class SourceDraft(
    val name: String,
    val type: SourceType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val playlistUrl: String = "",
    val localUri: String = ""
)

data class ImportProgress(val processed: Int, val message: String, val complete: Boolean = false)

sealed interface ConnectionResult {
    data class Success(val accountName: String? = null) : ConnectionResult
    data object AuthenticationFailed : ConnectionResult
    data object ServerUnavailable : ConnectionResult
    data class InvalidDetails(val reason: String) : ConnectionResult
}
