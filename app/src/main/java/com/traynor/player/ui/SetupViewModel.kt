package com.traynor.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.traynor.player.AppContainer
import com.traynor.player.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

data class ProgrammePreview(val title: String? = null, val loading: Boolean = false, val supplied: Boolean = false)
data class LiveUiState(val sourceId: Long? = null, val categories: List<String> = emptyList(), val selectedCategory: String? = null, val channels: List<com.traynor.player.data.local.ChannelEntity> = emptyList(), val query: String = "", val loading: Boolean = true, val programmePreviews: Map<Long, ProgrammePreview> = emptyMap())
class LiveViewModel(private val container: AppContainer) : ViewModel() {
    private val category = MutableStateFlow<String?>(null); private val query = MutableStateFlow("")
    private val previews = MutableStateFlow<Map<Long, ProgrammePreview>>(emptyMap())
    private val requestedPreviews = mutableSetOf<Long>()
    private val epgRequests = Semaphore(3)
    private val libraryState = container.preferences.activeSourceId.filterNotNull().flatMapLatest { sourceId ->
        combine(container.database.channelDao().categories(sourceId), category, query) { cats, cat, q -> Triple(cats, cat, q) }
            .flatMapLatest { (cats, cat, q) -> container.database.channelDao().observePage(sourceId, cat, q).map { LiveUiState(sourceId, cats, cat, it, q, false) } }
    }
    val state: StateFlow<LiveUiState> = combine(libraryState, previews) { library, programmePreviews -> library.copy(programmePreviews = programmePreviews) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())
    fun selectCategory(value: String?) { category.value = value }
    fun search(value: String) { query.value = value }
    suspend fun playableUrls(id: Long) = container.sourceRepository.playableUrls(id)
    fun remember(id: Long) = viewModelScope.launch { container.preferences.rememberChannel(id) }
    fun loadProgrammePreview(channelId: Long) {
        if (!requestedPreviews.add(channelId)) return
        previews.update { it + (channelId to ProgrammePreview(loading = true)) }
        viewModelScope.launch {
            val programme = epgRequests.withPermit {
                runCatching { container.sourceRepository.guideForChannel(channelId) }.getOrDefault(emptyList()).let { listings ->
                    val now = System.currentTimeMillis()
                    listings.firstOrNull { it.startMillis != null && it.endMillis != null && it.startMillis <= now && it.endMillis > now }
                        ?: listings.firstOrNull { it.startMillis != null && it.startMillis > now }
                        ?: listings.firstOrNull()
                }
            }
            previews.update { it + (channelId to ProgrammePreview(title = programme?.title, supplied = programme != null)) }
        }
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = LiveViewModel(container) as T
    } }
}

data class GuideUiState(
    val channels: List<com.traynor.player.data.local.ChannelEntity> = emptyList(),
    val selectedChannel: com.traynor.player.data.local.ChannelEntity? = null,
    val programmes: List<com.traynor.player.data.repository.GuideProgramme> = emptyList(),
    val loading: Boolean = true,
    val loadingProgrammes: Boolean = false
)
class GuideViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val channels = container.preferences.activeSourceId.filterNotNull().flatMapLatest { sourceId ->
        container.database.channelDao().observePage(sourceId, null, "", limit = 500)
    }
    private val selection = combine(channels, selectedId) { entries, selected -> entries to entries.firstOrNull { it.id == selected } }
    val state = selection.flatMapLatest { (entries, selected) ->
        if (selected == null) flowOf(GuideUiState(channels = entries, loading = false))
        else flow {
            emit(GuideUiState(entries, selected, loading = false, loadingProgrammes = true))
            val programmes = runCatching { container.sourceRepository.guideForChannel(selected.id) }.getOrDefault(emptyList())
            emit(GuideUiState(entries, selected, programmes, loading = false))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideUiState())
    fun select(id: Long) { selectedId.value = id }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = GuideViewModel(container) as T
    } }
}

data class MoviesUiState(val sourceId: Long? = null, val categories: List<String> = emptyList(), val selectedCategory: String? = null, val movies: List<com.traynor.player.data.local.MovieEntity> = emptyList(), val query: String = "", val loading: Boolean = true)
class MoviesViewModel(private val container: AppContainer) : ViewModel() {
    private val category = MutableStateFlow<String?>(null); private val query = MutableStateFlow("")
    val state: StateFlow<MoviesUiState> = container.preferences.activeSourceId.filterNotNull().flatMapLatest { sourceId ->
        combine(container.database.movieDao().categories(sourceId), category, query) { categories, selected, term -> Triple(categories, selected, term) }
            .flatMapLatest { (categories, selected, term) -> container.database.movieDao().observePage(sourceId, selected, term).map { MoviesUiState(sourceId, categories, selected, it, term, false) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoviesUiState())
    fun selectCategory(value: String?) { category.value = value }
    fun search(value: String) { query.value = value }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = MoviesViewModel(container) as T
    } }
}

data class SeriesUiState(val sourceId: Long? = null, val categories: List<String> = emptyList(), val selectedCategory: String? = null, val series: List<com.traynor.player.data.local.SeriesEntity> = emptyList(), val query: String = "", val loading: Boolean = true)
class SeriesViewModel(private val container: AppContainer) : ViewModel() {
    private val category = MutableStateFlow<String?>(null); private val query = MutableStateFlow("")
    val state: StateFlow<SeriesUiState> = container.preferences.activeSourceId.filterNotNull().flatMapLatest { sourceId ->
        combine(container.database.seriesDao().categories(sourceId), category, query) { categories, selected, term -> Triple(categories, selected, term) }
            .flatMapLatest { (categories, selected, term) -> container.database.seriesDao().observePage(sourceId, selected, term).map { SeriesUiState(sourceId, categories, selected, it, term, false) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesUiState())
    fun selectCategory(value: String?) { category.value = value }
    fun search(value: String) { query.value = value }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = SeriesViewModel(container) as T
    } }
}

data class SeriesDetailUiState(val loading: Boolean = true, val series: com.traynor.player.data.local.SeriesEntity? = null, val episodes: List<com.traynor.player.data.repository.SeriesEpisode> = emptyList(), val error: Boolean = false)
class SeriesDetailViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(SeriesDetailUiState())
    val state = mutable.asStateFlow()
    fun load(id: Long) = viewModelScope.launch {
        val series = container.database.seriesDao().get(id)
        if (series == null) { mutable.value = SeriesDetailUiState(loading = false, error = true); return@launch }
        mutable.value = SeriesDetailUiState(series = series)
        runCatching { container.sourceRepository.seriesEpisodes(id) }
            .onSuccess { mutable.value = SeriesDetailUiState(loading = false, series = series, episodes = it) }
            .onFailure { mutable.value = SeriesDetailUiState(loading = false, series = series, error = true) }
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = SeriesDetailViewModel(container) as T
    } }
}

data class MovieDetailUiState(val loading: Boolean = true, val details: com.traynor.player.data.repository.MovieDetails? = null, val availability: com.traynor.player.data.network.UkAvailability? = null, val availabilityConfigured: Boolean = false)
class MovieDetailViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(MovieDetailUiState())
    val state = mutable.asStateFlow()
    fun load(id: Long) = viewModelScope.launch {
        mutable.value = MovieDetailUiState(true)
        val details = runCatching { container.sourceRepository.movieDetails(id) }.getOrNull()
        val key = container.preferences.tmdbApiKey.first()
        val movie = container.database.movieDao().get(id)
        val availability = if (key.isNotBlank() && movie != null) runCatching { container.tmdbRepository.movieUkAvailability(key, movie.title, details?.year ?: movie.year, details?.tmdbId) }.getOrNull() else null
        mutable.value = MovieDetailUiState(false, details, availability, key.isNotBlank())
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = MovieDetailViewModel(container) as T
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
    data class Idle(val lastCheckedAt: Long? = null) : UpdateUiState
    data class Checking(val lastCheckedAt: Long? = null) : UpdateUiState
    data class UpToDate(val latestVersion: String, val lastCheckedAt: Long) : UpdateUiState
    data class Available(val update: com.traynor.player.data.network.AvailableUpdate, val lastCheckedAt: Long) : UpdateUiState
    data class Downloading(val update: com.traynor.player.data.network.AvailableUpdate, val progress: Int) : UpdateUiState
    data class ReadyToInstall(val verified: com.traynor.player.data.network.VerifiedUpdate) : UpdateUiState
    data class PermissionRequired(val verified: com.traynor.player.data.network.VerifiedUpdate) : UpdateUiState
    data class InstallerOpened(val version: String) : UpdateUiState
    data class DebugBuild(val latestVersion: String, val releaseUrl: String, val lastCheckedAt: Long) : UpdateUiState
    data class Failed(val message: String, val lastCheckedAt: Long? = null) : UpdateUiState
}
class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch {
        val last = container.preferences.lastUpdateCheckAt.first()
        mutable.value = UpdateUiState.Idle(last)
        if (last == null || System.currentTimeMillis() - last >= 24 * 60 * 60 * 1_000L) check()
    } }
    fun check() = viewModelScope.launch {
        val last = container.preferences.lastUpdateCheckAt.first()
        mutable.value = UpdateUiState.Checking(last)
        runCatching { container.releaseRepository.check(com.traynor.player.BuildConfig.VERSION_NAME) }
            .onSuccess { result ->
                val checked = System.currentTimeMillis(); container.preferences.markUpdateChecked(checked)
                mutable.value = if (com.traynor.player.BuildConfig.DEBUG) UpdateUiState.DebugBuild(result.latestVersion, result.releaseUrl, checked)
                    else result.update?.let { UpdateUiState.Available(it, checked) } ?: UpdateUiState.UpToDate(result.latestVersion, checked)
            }.onFailure { mutable.value = UpdateUiState.Failed("Could not check for updates. Check your connection and try again.", last) }
    }
    fun download(update: com.traynor.player.data.network.AvailableUpdate) = viewModelScope.launch {
        mutable.value = UpdateUiState.Downloading(update, 0)
        runCatching { container.updateInstaller.downloadAndVerify(update) { progress -> mutable.value = UpdateUiState.Downloading(update, progress) } }
            .onSuccess { mutable.value = UpdateUiState.ReadyToInstall(it) }
            .onFailure { mutable.value = UpdateUiState.Failed("The update could not be verified. Nothing was installed.", container.preferences.lastUpdateCheckAt.first()) }
    }
    fun install(verified: com.traynor.player.data.network.VerifiedUpdate) {
        mutable.value = when (container.updateInstaller.openInstaller(verified)) {
            com.traynor.player.data.network.InstallLaunchResult.PermissionRequired -> UpdateUiState.PermissionRequired(verified)
            com.traynor.player.data.network.InstallLaunchResult.InstallerOpened -> UpdateUiState.InstallerOpened(verified.update.version)
        }
    }
    companion object { fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = UpdateViewModel(container) as T
    } }
}
