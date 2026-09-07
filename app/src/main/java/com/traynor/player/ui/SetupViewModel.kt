package com.traynor.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.traynor.player.AppContainer
import com.traynor.player.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetupUiState(val busy: Boolean = false, val status: String? = null, val error: String? = null, val complete: Boolean = false)

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(SetupUiState())
    val state = mutable.asStateFlow()
    fun testAndSave(draft: SourceDraft) = viewModelScope.launch {
        mutable.value = SetupUiState(true, "Connecting…")
        when (val result = container.sourceRepository.test(draft)) {
            is ConnectionResult.Success -> {
                mutable.value = SetupUiState(true, "Connected. Importing your library…")
                runCatching {
                    var sourceId = 0L
                    container.sourceRepository.addAndImport(draft).collect { progress ->
                        if (progress.complete && progress.message == "Source ready") sourceId = progress.processed.toLong()
                        mutable.value = SetupUiState(true, progress.message)
                    }
                    check(sourceId > 0); container.preferences.finishSetup(sourceId)
                }.onSuccess { mutable.value = SetupUiState(status = "Ready", complete = true) }
                    .onFailure { mutable.value = SetupUiState(error = safeMessage(it)) }
            }
            ConnectionResult.AuthenticationFailed -> mutable.value = SetupUiState(error = "Authentication failed")
            ConnectionResult.ServerUnavailable -> mutable.value = SetupUiState(error = "Server unavailable")
            is ConnectionResult.InvalidDetails -> mutable.value = SetupUiState(error = result.reason)
        }
    }
    private fun safeMessage(error: Throwable) = when {
        error.message?.contains("401") == true || error.message?.contains("403") == true -> "Authentication failed"
        error is java.net.SocketTimeoutException -> "The source timed out"
        else -> "Could not import this source. Check the details and try again."
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = SetupViewModel(container) as T
    } }
}

data class LiveUiState(val sourceId: Long? = null, val categories: List<String> = emptyList(), val selectedCategory: String? = null, val channels: List<com.traynor.player.data.local.ChannelEntity> = emptyList(), val query: String = "", val loading: Boolean = true)
class LiveViewModel(private val container: AppContainer) : ViewModel() {
    private val category = MutableStateFlow<String?>(null); private val query = MutableStateFlow("")
    val state: StateFlow<LiveUiState> = container.preferences.activeSourceId.filterNotNull().flatMapLatest { sourceId ->
        combine(container.database.channelDao().categories(sourceId), category, query) { cats, cat, q -> Triple(cats, cat, q) }
            .flatMapLatest { (cats, cat, q) -> container.database.channelDao().observePage(sourceId, cat, q).map { LiveUiState(sourceId, cats, cat, it, q, false) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())
    fun selectCategory(value: String?) { category.value = value }
    fun search(value: String) { query.value = value }
    suspend fun playableUrls(id: Long) = container.sourceRepository.playableUrls(id)
    fun remember(id: Long) = viewModelScope.launch { container.preferences.rememberChannel(id) }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = LiveViewModel(container) as T
    } }
}

data class SourceRefreshUiState(val sourceId: Long? = null, val message: String? = null, val running: Boolean = false, val failed: Boolean = false)
class SourceRefreshViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(SourceRefreshUiState())
    val state = mutable.asStateFlow()
    fun refresh(sourceId: Long) = viewModelScope.launch {
        mutable.value = SourceRefreshUiState(sourceId, "Refreshing source…", running = true)
        runCatching { container.sourceRepository.refresh(sourceId).collect { mutable.value = SourceRefreshUiState(sourceId, it.message, running = !it.complete) } }
            .onFailure { mutable.value = SourceRefreshUiState(sourceId, "Could not refresh this source", failed = true) }
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = SourceRefreshViewModel(container) as T
    } }
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: com.traynor.player.data.network.AvailableUpdate) : UpdateUiState
    data object Unavailable : UpdateUiState
}
class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state = mutable.asStateFlow()
    fun check() = viewModelScope.launch {
        mutable.value = UpdateUiState.Checking
        mutable.value = runCatching { container.releaseRepository.latestApk(com.traynor.player.BuildConfig.VERSION_NAME) }
            .fold(onSuccess = { if (it == null) UpdateUiState.UpToDate else UpdateUiState.Available(it) }, onFailure = { UpdateUiState.Unavailable })
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = UpdateViewModel(container) as T
    } }
}
