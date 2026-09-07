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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.traynor.player.AppContainer
import com.traynor.player.ui.LiveViewModel
import kotlinx.coroutines.delay

@Composable fun VideoPlayer(channelId: Long, container: AppContainer, enterPip: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current; val model: LiveViewModel = viewModel(factory = LiveViewModel.factory(container))
    var urls by remember { mutableStateOf(emptyList<String>()) }; var candidateIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }; var controls by remember { mutableStateOf(true) }; var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var buffering by remember { mutableStateOf(false) }; var ready by remember { mutableStateOf(false) }; var attempt by remember { mutableIntStateOf(0) }
    val httpFactory = remember { DefaultHttpDataSource.Factory().setUserAgent("Player/1.0 (Android)").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(45_000) }
    val player = remember { ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1_500, 15_000, 500, 1_000).build()).build().apply { playWhenReady = true } }
    val url = urls.getOrNull(candidateIndex)
    LaunchedEffect(channelId) { urls = model.playableUrls(channelId); candidateIndex = 0; if (urls.isEmpty()) error = "This channel is unavailable" }
    LaunchedEffect(url, attempt) { url?.let {
        error = null; ready = false; buffering = true
        player.setMediaItem(MediaItem.Builder().setUri(it).setMimeType(it.guessMimeType()).build()); player.prepare(); player.play()
    } }
    LaunchedEffect(url, attempt) { if (url != null) { delay(20_000); if (!ready && error == null) { buffering = false; error = "This stream is taking too long to respond. Try again or choose another channel." } } }
    LaunchedEffect(controls) { if (controls) { delay(4_000); controls = false } }
    // The listener survives candidate changes; releasing the player between
    // fallback URLs would turn a recoverable error into a permanent black screen.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_READY) ready = true; if (state == Player.STATE_ENDED) error = "This live stream ended" }
            override fun onPlayerError(playerError: PlaybackException) {
                buffering = false
                if (!ready && candidateIndex < urls.lastIndex) {
                    candidateIndex++
                    return
                }
                error = when (playerError.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network connection interrupted"
                    else -> "This stream could not be played. The source may use a format this device does not support."
                }
            }
        }
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
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                isClickable = true
                // Live controls intentionally fade after inactivity. A normal
                // tap on the picture always brings them back (or hides them).
                setOnClickListener { controls = !controls }
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                this.resizeMode = resizeMode
            } },
            update = { it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && error == null) Row(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f), MaterialTheme.shapes.large).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(12.dp)); Text(if (candidateIndex > 0) "Trying compatible stream…" else "Buffering stream…", color = Color.White) }
        AnimatedVisibility(controls) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))) { Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Spacer(Modifier.width(8.dp)); Column { Text("Live TV", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("Live stream", color = Color.White.copy(alpha = .75f)) } }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ /* previous channel foundation */ }) { Icon(Icons.Default.SkipPrevious, "Previous channel", tint = Color.White) }
                FilledIconButton({ if (player.isPlaying) player.pause() else player.play() }, Modifier.size(64.dp)) { Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", Modifier.size(36.dp)) }
                IconButton({ /* next channel foundation */ }) { Icon(Icons.Default.SkipNext, "Next channel", tint = Color.White) }
                IconButton({ resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) { Icon(Icons.Default.AspectRatio, "Fit or fill", tint = Color.White) }
                IconButton(enterPip) { Icon(Icons.Default.PictureInPictureAlt, "Picture in Picture", tint = Color.White) }
            }
        } }
        error?.let { message -> Card(Modifier.align(Alignment.Center).padding(24.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp)); Text(message, style = MaterialTheme.typography.titleMedium); Button({ candidateIndex = 0; attempt++; error = null }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Retry") } } } }
    }
}

private fun String.guessMimeType(): String? = substringBefore('?').lowercase().let { path -> when {
    path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
    path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
    path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
    else -> null
} }
