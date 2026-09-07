@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.traynor.player.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.traynor.player.AppContainer
import com.traynor.player.ui.LiveViewModel
import kotlinx.coroutines.delay

@Composable fun VideoPlayer(channelId: Long, container: AppContainer, enterPip: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current; val model: LiveViewModel = viewModel(factory = LiveViewModel.factory(container))
    var url by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var controls by remember { mutableStateOf(true) }; var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    LaunchedEffect(channelId) { url = model.playableUrl(channelId); if (url == null) error = "This channel is unavailable" }
    LaunchedEffect(url) { url?.let { player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.play() } }
    LaunchedEffect(controls) { if (controls) { delay(4_000); controls = false } }
    DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onPlayerError(playerError: PlaybackException) { error = when (playerError.errorCode) { PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network connection interrupted"; else -> "This stream could not be played" } } }
        player.addListener(listener); onDispose { player.removeListener(listener); player.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).focusable().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { controls = true; if (player.isPlaying) player.pause() else player.play(); true }
            Key.Back -> { if (controls) controls = false else close(); true }
            Key.DirectionUp -> { controls = true; true }
            Key.DirectionDown -> { controls = true; true }
            else -> { controls = true; false }
        }
    }) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false; layoutParams = ViewGroup.LayoutParams(-1, -1); this.resizeMode = resizeMode } }, update = { it.resizeMode = resizeMode }, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(controls) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))) { Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Spacer(Modifier.width(8.dp)); Column { Text("Live TV", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("Live stream", color = Color.White.copy(alpha = .75f)) } }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ /* previous channel foundation */ }) { Icon(Icons.Default.SkipPrevious, "Previous channel", tint = Color.White) }
                FilledIconButton({ if (player.isPlaying) player.pause() else player.play() }, Modifier.size(64.dp)) { Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", Modifier.size(36.dp)) }
                IconButton({ /* next channel foundation */ }) { Icon(Icons.Default.SkipNext, "Next channel", tint = Color.White) }
                IconButton({ resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) { Icon(Icons.Default.AspectRatio, "Fit or fill", tint = Color.White) }
                IconButton(enterPip) { Icon(Icons.Default.PictureInPictureAlt, "Picture in Picture", tint = Color.White) }
            }
        } }
        error?.let { message -> Card(Modifier.align(Alignment.Center).padding(24.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp)); Text(message, style = MaterialTheme.typography.titleMedium); Button({ error = null; player.prepare(); player.play() }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Retry") } } } }
    }
}
