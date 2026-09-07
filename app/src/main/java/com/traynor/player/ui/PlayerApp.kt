@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.traynor.player.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.traynor.player.AppContainer
import com.traynor.player.BuildConfig
import com.traynor.player.R
import com.traynor.player.core.model.*
import com.traynor.player.data.local.ChannelEntity
import com.traynor.player.ui.player.VideoPlayer
import com.traynor.player.ui.player.PlaybackType
import com.traynor.player.ui.theme.PlayerTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private enum class Destination(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home), Live("live", "Live TV", Icons.Default.LiveTv), Movies("movies", "Movies", Icons.Default.Movie),
    Series("series", "Series", Icons.Default.VideoLibrary), Guide("guide", "TV Guide", Icons.Default.CalendarMonth),
    Search("search", "Search", Icons.Default.Search), Favourites("favourites", "Favourites", Icons.Default.Favorite), Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable fun PlayerApp(container: AppContainer, enterPip: (android.util.Rational) -> Unit, inPip: Boolean) = PlayerTheme {
    val setupFlow = remember(container.preferences) { container.preferences.setupComplete.map<Boolean, Boolean?> { it } }
    val setup by setupFlow.collectAsStateWithLifecycle(initialValue = null)
    Surface(Modifier.fillMaxSize()) {
        when (setup) { null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            false -> SetupWizard(container)
            true -> MainShell(container, enterPip, inPip)
        }
    }
}

@Composable private fun SetupWizard(container: AppContainer) {
    var type by remember { mutableStateOf<SourceType?>(null) }
    var name by remember { mutableStateOf("") }; var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }; var localUri by remember { mutableStateOf("") }
    val context = LocalContext.current
    val model: SetupViewModel = viewModel(factory = SetupViewModel.factory(container)); val state by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; localUri = uri.toString() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 680.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(painterResource(R.drawable.player_mark), null, Modifier.size(48.dp), tint = Color.Unspecified)
                Text("Add your TV source", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Player contains no channels or subscriptions. Add a legitimate source you control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(type, label = "setup") { selected ->
                    if (selected == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SourceChoice("Xtream Codes", "Connect with server credentials", Icons.Default.Dns) { type = SourceType.XTREAM; name = "My TV" }
                        SourceChoice("M3U Playlist", "Use a remote M3U or M3U8 URL", Icons.Default.Link) { type = SourceType.REMOTE_M3U; name = "My Playlist" }
                        SourceChoice("Local Playlist", "Choose an M3U file stored on this device", Icons.Default.FolderOpen) { type = SourceType.LOCAL_M3U; name = "Local Playlist"; picker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) }
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Profile name") }, singleLine = true)
                        when (selected) {
                            SourceType.XTREAM -> { OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, placeholder = { Text("https://example.com:port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)); OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
                            SourceType.REMOTE_M3U -> OutlinedTextField(playlist, { playlist = it }, Modifier.fillMaxWidth(), label = { Text("Playlist URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                            SourceType.LOCAL_M3U -> OutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (localUri.isBlank()) "Choose playlist file" else "Playlist selected") }
                        }
                        state.status?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, color = MaterialTheme.colorScheme.primary) }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton({ type = null }, enabled = !state.busy) { Text("Back") }
                            Button({ model.testAndSave(SourceDraft(name, selected, server, username, password, playlist, localUri)) }, enabled = !state.busy && name.isNotBlank() && when(selected) { SourceType.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank(); SourceType.REMOTE_M3U -> playlist.isNotBlank(); SourceType.LOCAL_M3U -> localUri.isNotBlank() }) { Text("Test & add source") }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SourceChoice(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    OutlinedCard(onClick, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null) }
    }
}

@Composable private fun MainShell(container: AppContainer, enterPip: (android.util.Rational) -> Unit, inPip: Boolean) {
    val nav = rememberNavController(); val backStack by nav.currentBackStackEntryAsState(); val current = backStack?.destination?.route
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    BoxWithConstraints {
        val rail = isTv || maxWidth >= 840.dp
        val isPlayer = current?.let { it.startsWith("player/") || it.startsWith("movie-player/") || it.startsWith("episode/") } == true
        Row(Modifier.fillMaxSize()) {
            if (rail && !isPlayer) NavigationRail(header = { Icon(painterResource(R.drawable.player_mark), "Player", Modifier.padding(16.dp).size(40.dp), tint = Color.Unspecified) }) {
                Destination.entries.forEach { item -> NavigationRailItem(selected = current == item.route, onClick = { navigate(nav, item.route) }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) }
            }
            Scaffold(bottomBar = { if (!rail && !isPlayer) NavigationBar { listOf(Destination.Home, Destination.Live, Destination.Movies, Destination.Search, Destination.Settings).forEach { item -> NavigationBarItem(selected = current == item.route, onClick = { navigate(nav, item.route) }, icon = { Icon(item.icon, item.title) }) } } }) { padding ->
                NavHost(nav, Destination.Home.route, if (isPlayer) Modifier.fillMaxSize() else Modifier.padding(padding)) {
                    composable("home") { Dashboard(container, { navigate(nav, "live") }, { navigate(nav, "movies") }, { navigate(nav, "series") }, { navigate(nav, "guide") }, { channelId -> nav.navigate("player/$channelId") }) }
                    composable("live") { LiveScreen(container, { nav.navigate("player/$it") }, { nav.navigate("guide") }) }
                    composable("player/{id}") { entry -> VideoPlayer(entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable, container, enterPip, inPip, { nav.popBackStack() }) }
                    composable("movies") { MoviesScreen(container) { nav.navigate("movie/$it") } }
                    composable("movie/{id}") { entry -> MovieDetailScreen(entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable, container, { nav.navigate("movie-player/$it") }, { nav.popBackStack() }) }
                    composable("movie-player/{id}") { entry -> VideoPlayer(entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable, container, enterPip, inPip, { nav.popBackStack() }, PlaybackType.MOVIE) }
                    composable("series") { SeriesScreen(container) { nav.navigate("series/$it") } }
                    composable("series/{id}") { entry -> SeriesDetailScreen(entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable, container, { episode -> nav.navigate("episode/${episode.seriesId}/${Uri.encode(episode.episodeId)}/${Uri.encode(episode.extension ?: "mp4")}") }, { nav.popBackStack() }) }
                    composable("episode/{seriesId}/{episodeId}/{extension}") { entry -> VideoPlayer(entry.arguments?.getString("seriesId")?.toLongOrNull() ?: return@composable, container, enterPip, inPip, { nav.popBackStack() }, PlaybackType.EPISODE, Uri.decode(entry.arguments?.getString("episodeId").orEmpty()), Uri.decode(entry.arguments?.getString("extension").orEmpty())) }
                    composable("guide") { GuideScreen(container) { nav.navigate("player/$it") } }
                    composable("search") { FoundationScreen("Universal search", "Live channels are searchable now; Movies and Series will join this screen.", Icons.Default.Search) }
                    composable("favourites") { FoundationScreen("Favourites", "The local favourites database is ready for Channels, Movies and Series.", Icons.Default.Favorite) }
                    composable("settings") { SettingsScreen(container) }
                }
            }
        }
    }
}

private fun navigate(nav: androidx.navigation.NavHostController, route: String) = nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }

@Composable private fun Dashboard(
    container: AppContainer,
    openLive: () -> Unit,
    openMovies: () -> Unit,
    openSeries: () -> Unit,
    openGuide: () -> Unit,
    resumeChannel: (Long) -> Unit
) {
    val sources by container.sourceRepository.sources().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSourceId by container.preferences.activeSourceId.collectAsStateWithLifecycle(initialValue = null)
    val lastChannelId by container.preferences.lastChannelId.collectAsStateWithLifecycle(initialValue = null)
    val source = sources.firstOrNull { it.id == activeSourceId }
    val channelCount by remember(activeSourceId) { activeSourceId?.let { container.database.channelDao().observeCount(it) } ?: flowOf(0) }.collectAsStateWithLifecycle(initialValue = 0)
    val movieCount by remember(activeSourceId) { activeSourceId?.let { container.database.movieDao().observeCount(it) } ?: flowOf(0) }.collectAsStateWithLifecycle(initialValue = 0)
    val seriesCount by remember(activeSourceId) { activeSourceId?.let { container.database.seriesDao().observeCount(it) } ?: flowOf(0) }.collectAsStateWithLifecycle(initialValue = 0)
    val lastChannel by remember(lastChannelId) { lastChannelId?.let { container.database.channelDao().observe(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
    val greeting = remember { when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" } }

    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(source?.let { "${it.name} is ready on this device." } ?: "Your television, your sources, on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard(onClick = openLive, Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(64.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.LiveTv, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Live TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (channelCount > 0) "$channelCount channels ready to watch" else "Browse channels and start watching", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (lastChannel != null) item {
            FilledTonalButton({ resumeChannel(lastChannel!!.id) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Resume ${lastChannel!!.name}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        item { Text("Explore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeAction("Movies", if (movieCount > 0) "$movieCount available" else "Your library", Icons.Default.Movie, openMovies, Modifier.weight(1f))
                HomeAction("Series", if (seriesCount > 0) "$seriesCount available" else "Your library", Icons.Default.VideoLibrary, openSeries, Modifier.weight(1f))
                HomeAction("Guide", "What's on now", Icons.Default.CalendarMonth, openGuide, Modifier.weight(1f))
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Your sources and viewing history stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun HomeAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun FoundationScreen(title: String, text: String, icon: ImageVector) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { Icon(icon, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun SettingsScreen(container: AppContainer) {
    val sources by container.sourceRepository.sources().collectAsStateWithLifecycle(initialValue = emptyList())
    val updateModel: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(container))
    val updateState by updateModel.state.collectAsStateWithLifecycle()
    val refreshModel: SourceRefreshViewModel = viewModel(factory = SourceRefreshViewModel.factory(container))
    val refreshState by refreshModel.state.collectAsStateWithLifecycle()
    val hasTmdbKey by container.preferences.hasTmdbApiKey.collectAsStateWithLifecycle(initialValue = false)
    var editedTmdbKey by remember { mutableStateOf("") }
    var metadataStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Sources", style = MaterialTheme.typography.titleLarge) }
        items(sources, key = { it.id }) { source -> ListItem({ Text(source.name) }, supportingContent = { Text(if (refreshState.sourceId == source.id && refreshState.message != null) refreshState.message.orEmpty() else "${source.type.name.replace('_',' ')} • ${source.lastRefreshedAt?.let { "Last refreshed ${java.text.DateFormat.getDateTimeInstance().format(it)}" } ?: "Not refreshed"}") }, leadingContent = { Icon(Icons.Default.Storage, null) }, trailingContent = { IconButton({ refreshModel.refresh(source.id) }, enabled = refreshState.sourceId != source.id || !refreshState.running) { Icon(Icons.Default.Refresh, "Refresh source") } }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
        item { Text("Playback", style = MaterialTheme.typography.titleLarge); ListItem({ Text("Media3 / ExoPlayer") }, supportingContent = { Text("Hardware decoding, HLS, DASH and progressive playback") }, leadingContent = { Icon(Icons.Default.PlayCircle, null) }) }
        item {
            Text("Movie metadata", style = MaterialTheme.typography.titleLarge)
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("UK streaming availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Optional TMDB v3 API key or Read Access Token. It is encrypted on this device and used only to check legal UK providers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasTmdbKey) Text("API key saved securely on this device.", color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(editedTmdbKey, { editedTmdbKey = it; metadataStatus = null }, Modifier.fillMaxWidth(), label = { Text(if (hasTmdbKey) "Replace TMDB key" else "TMDB API key") }, placeholder = { Text(if (hasTmdbKey) "Saved securely — paste only to replace" else "Paste key or Read Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                metadataStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({ scope.launch { if (editedTmdbKey.isNotBlank()) { container.preferences.setTmdbApiKey(editedTmdbKey); editedTmdbKey = ""; metadataStatus = "Key saved securely" } } }, enabled = editedTmdbKey.isNotBlank()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text(if (hasTmdbKey) "Replace key" else "Save key") }
                    if (hasTmdbKey) OutlinedButton({ scope.launch { container.preferences.setTmdbApiKey(""); metadataStatus = "Key removed" } }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Remove") }
                }
            } }
        }
        item {
            Text("App updates", style = MaterialTheme.typography.titleLarge)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Signed APK releases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    when (val state = updateState) {
                        is UpdateUiState.Idle -> UpdateSummary("Installed: v${BuildConfig.VERSION_NAME}", "Check GitHub for an official signed update.", state.lastCheckedAt)
                        is UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Checking GitHub…") }
                        is UpdateUiState.UpToDate -> UpdateSummary("You’re up to date", "Installed: v${BuildConfig.VERSION_NAME} • Latest: v${state.latestVersion}", state.lastCheckedAt, MaterialTheme.colorScheme.primary)
                        is UpdateUiState.Available -> { UpdateSummary("Update available", "Installed: v${BuildConfig.VERSION_NAME} • Available: v${state.update.version}", state.lastCheckedAt, MaterialTheme.colorScheme.primary); state.update.notes?.let { Text(it, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button({ updateModel.download(state.update) }) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download update") } }
                        is UpdateUiState.Downloading -> { Text("Downloading update ${state.progress}%", color = MaterialTheme.colorScheme.primary); LinearProgressIndicator({ state.progress / 100f }, Modifier.fillMaxWidth()) }
                        is UpdateUiState.ReadyToInstall -> { Text("Ready to install v${state.verified.update.version}", color = MaterialTheme.colorScheme.primary); Text("Verified signed APK • ${state.verified.sha256.take(12)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Button({ updateModel.install(state.verified) }) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("Install update") } }
                        is UpdateUiState.PermissionRequired -> { Text("Allow Player to install updates, then tap Install update again.", color = MaterialTheme.colorScheme.error); Button({ updateModel.install(state.verified) }) { Text("Allow installs") } }
                        is UpdateUiState.InstallerOpened -> Text("Android’s installer is open for Player v${state.version}.", color = MaterialTheme.colorScheme.primary)
                        is UpdateUiState.DebugBuild -> { UpdateSummary("Official release available", "This is a debug build (v${BuildConfig.VERSION_NAME}). Install v${state.latestVersion} once from GitHub; future signed updates install over it.", state.lastCheckedAt); OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.releaseUrl))) }) { Text("Open official release") } }
                        is UpdateUiState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                    if (updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading && updateState !is UpdateUiState.ReadyToInstall && updateState !is UpdateUiState.PermissionRequired && updateState !is UpdateUiState.InstallerOpened) OutlinedButton(updateModel::check) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Check for updates") }
                }
            }
        }
        item { Text("Storage", style = MaterialTheme.typography.titleLarge); OutlinedButton({ /* confirmation UI is added with history screen */ }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear history") } }
        item { Text("About", style = MaterialTheme.typography.titleLarge); Text("Player ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nNative Android • com.traynor.player", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun UpdateSummary(title: String, detail: String, checkedAt: Long?, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    checkedAt?.let { Text("Last checked: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
