@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.traynor.player.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
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
import com.traynor.player.core.model.*
import com.traynor.player.data.local.ChannelEntity
import com.traynor.player.ui.player.VideoPlayer
import com.traynor.player.ui.theme.PlayerTheme
import kotlinx.coroutines.flow.map

private enum class Destination(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home), Live("live", "Live TV", Icons.Default.LiveTv), Movies("movies", "Movies", Icons.Default.Movie),
    Series("series", "Series", Icons.Default.VideoLibrary), Guide("guide", "TV Guide", Icons.Default.CalendarMonth),
    Search("search", "Search", Icons.Default.Search), Favourites("favourites", "Favourites", Icons.Default.Favorite), Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable fun PlayerApp(container: AppContainer, enterPip: () -> Unit) = PlayerTheme {
    val setupFlow = remember(container.preferences) { container.preferences.setupComplete.map<Boolean, Boolean?> { it } }
    val setup by setupFlow.collectAsStateWithLifecycle(initialValue = null)
    Surface(Modifier.fillMaxSize()) {
        when (setup) { null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            false -> SetupWizard(container)
            true -> MainShell(container, enterPip)
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
                Icon(Icons.Default.LiveTv, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
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

@Composable private fun MainShell(container: AppContainer, enterPip: () -> Unit) {
    val nav = rememberNavController(); val backStack by nav.currentBackStackEntryAsState(); val current = backStack?.destination?.route
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    BoxWithConstraints {
        val rail = isTv || maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(header = { Icon(Icons.Default.PlayCircle, "Player", Modifier.padding(16.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary) }) {
                Destination.entries.forEach { item -> NavigationRailItem(selected = current == item.route, onClick = { navigate(nav, item.route) }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) }
            }
            Scaffold(bottomBar = { if (!rail) NavigationBar { listOf(Destination.Home, Destination.Live, Destination.Movies, Destination.Search, Destination.Settings).forEach { item -> NavigationBarItem(selected = current == item.route, onClick = { navigate(nav, item.route) }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) } } }) { padding ->
                NavHost(nav, Destination.Home.route, Modifier.padding(padding)) {
                    composable("home") { Dashboard { navigate(nav, "live") } }
                    composable("live") { LiveScreen(container) { nav.navigate("player/$it") } }
                    composable("player/{id}") { entry -> VideoPlayer(entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable, container, enterPip, { nav.popBackStack() }) }
                    composable("movies") { FoundationScreen("Movies", "Movie categories, metadata and resume data are architected next.", Icons.Default.Movie) }
                    composable("series") { FoundationScreen("Series", "Season, episode and independent progress support is next.", Icons.Default.VideoLibrary) }
                    composable("guide") { FoundationScreen("TV Guide", "XMLTV and the horizontally scrolling programme grid are next.", Icons.Default.CalendarMonth) }
                    composable("search") { FoundationScreen("Universal search", "Live channels are searchable now; Movies and Series will join this screen.", Icons.Default.Search) }
                    composable("favourites") { FoundationScreen("Favourites", "The local favourites database is ready for Channels, Movies and Series.", Icons.Default.Favorite) }
                    composable("settings") { SettingsScreen(container) }
                }
            }
        }
    }
}

private fun navigate(nav: androidx.navigation.NavHostController, route: String) = nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }

@Composable private fun Dashboard(openLive: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Good evening", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Your television, your sources, on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ElevatedCard(onClick = openLive, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Row(Modifier.padding(26.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LiveTv, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(20.dp)); Column(Modifier.weight(1f)) { Text("Live TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Browse channels and start watching") }; Icon(Icons.Default.PlayArrow, null, Modifier.size(36.dp)) } } }
        item { Text("Continue Watching and Recently Viewed appear here once you watch something.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun FoundationScreen(title: String, text: String, icon: ImageVector) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { Icon(icon, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun SettingsScreen(container: AppContainer) {
    val sources by container.sourceRepository.sources().collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Sources", style = MaterialTheme.typography.titleLarge) }
        items(sources, key = { it.id }) { source -> ListItem({ Text(source.name) }, supportingContent = { Text("${source.type.name.replace('_',' ')} • ${source.lastRefreshedAt?.let { "Last refreshed ${java.text.DateFormat.getDateTimeInstance().format(it)}" } ?: "Not refreshed"}") }, leadingContent = { Icon(Icons.Default.Storage, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
        item { Text("Playback", style = MaterialTheme.typography.titleLarge); ListItem({ Text("Media3 / ExoPlayer") }, supportingContent = { Text("Hardware decoding, HLS, DASH and progressive playback") }, leadingContent = { Icon(Icons.Default.PlayCircle, null) }) }
        item { Text("Storage", style = MaterialTheme.typography.titleLarge); OutlinedButton({ /* confirmation UI is added with history screen */ }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear history") } }
        item { Text("About", style = MaterialTheme.typography.titleLarge); Text("Player ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nNative Android • com.traynor.player", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
