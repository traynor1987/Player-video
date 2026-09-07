@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.traynor.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Rational
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.traynor.player.data.local.ChannelEntity
import com.traynor.player.data.repository.GuideProgramme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date

enum class PlaybackType { LIVE, MOVIE, EPISODE }

@Composable fun VideoPlayer(contentId: Long, container: AppContainer, enterPip: (Rational) -> Unit, inPip: Boolean, close: () -> Unit, type: PlaybackType = PlaybackType.LIVE, episodeId: String? = null, extension: String? = null) {
    val context = LocalContext.current
    var urls by remember { mutableStateOf(emptyList<String>()) }; var candidateIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }; var controls by remember { mutableStateOf(true) }; var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var buffering by remember { mutableStateOf(false) }; var ready by remember { mutableStateOf(false) }; var attempt by remember { mutableIntStateOf(0) }
    var pipAspectRatio by remember { mutableStateOf(Rational(16, 9)) }
    var positionMs by remember { mutableLongStateOf(0L) }; var durationMs by remember { mutableLongStateOf(0L) }; var scrubPosition by remember { mutableFloatStateOf(0f) }
    var liveChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var guide by remember { mutableStateOf<List<GuideProgramme>>(emptyList()) }
    var guideLoading by remember { mutableStateOf(false) }
    val onDemand = type != PlaybackType.LIVE
    val httpFactory = remember { DefaultHttpDataSource.Factory().setUserAgent("Player/1.0 (Android)").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(45_000) }
    val player = remember { ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1_500, 15_000, 500, 1_000).build()).build().apply { playWhenReady = true } }
    val url = urls.getOrNull(candidateIndex)
    LaunchedEffect(contentId, type, episodeId, extension) {
        urls = when (type) {
            PlaybackType.LIVE -> container.sourceRepository.playableUrls(contentId)
            PlaybackType.MOVIE -> container.sourceRepository.playableMovieUrls(contentId)
            PlaybackType.EPISODE -> episodeId?.let { container.sourceRepository.playableEpisodeUrls(contentId, it, extension) }.orEmpty()
        }
        candidateIndex = 0
        if (urls.isEmpty()) error = "This video is unavailable"
    }
    LaunchedEffect(contentId, type) {
        if (type == PlaybackType.LIVE) {
            liveChannel = container.database.channelDao().get(contentId)
            guideLoading = true
            guide = runCatching { container.sourceRepository.guideForChannel(contentId) }.getOrDefault(emptyList())
            guideLoading = false
        } else {
            liveChannel = null
            guide = emptyList()
            guideLoading = false
        }
    }
    LaunchedEffect(url, attempt) { url?.let {
        error = null; ready = false; buffering = true
        player.setMediaItem(MediaItem.Builder().setUri(it).setMimeType(it.guessMimeType()).build()); player.prepare(); player.play()
    } }
    LaunchedEffect(url, attempt) { if (url != null) { delay(20_000); if (!ready && error == null) { buffering = false; error = "This stream is taking too long to respond. Try again or choose another channel." } } }
    LaunchedEffect(controls) { if (controls) { delay(4_000); controls = false } }
    LaunchedEffect(player, onDemand) {
        if (onDemand) while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (durationMs > 0L) scrubPosition = positionMs.toFloat() / durationMs
            delay(500)
        }
    }
    // The listener survives candidate changes; releasing the player between
    // fallback URLs would turn a recoverable error into a permanent black screen.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_READY) ready = true; if (state == Player.STATE_ENDED) error = "This live stream ended" }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) pipAspectRatio = Rational(videoSize.width, videoSize.height)
            }
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
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).focusable().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { controls = true; if (player.isPlaying) player.pause() else player.play(); true }
            Key.Back -> { if (controls) controls = false else close(); true }
            Key.DirectionUp -> { controls = true; true }
            Key.DirectionDown -> { controls = true; true }
            Key.DirectionLeft -> if (onDemand) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); controls = true; true } else { controls = true; false }
            Key.DirectionRight -> if (onDemand) { player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)); controls = true; true } else { controls = true; false }
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
        if (!inPip && buffering && error == null) Row(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f), MaterialTheme.shapes.large).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(12.dp)); Text(if (candidateIndex > 0) "Trying compatible stream…" else "Buffering stream…", color = Color.White) }
        if (!inPip) AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) { Box(Modifier.fillMaxSize()) { Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Spacer(Modifier.width(8.dp)); Column { Text(if (type == PlaybackType.LIVE) liveChannel?.name ?: "Live TV" else if (type == PlaybackType.MOVIE) "Movie" else "Episode", color = Color.White, style = MaterialTheme.typography.titleLarge); Text(if (type == PlaybackType.LIVE) "Live stream" else "On-demand video", color = Color.White.copy(alpha = .75f)) } }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (type == PlaybackType.LIVE) MiniEpg(guide, guideLoading)
                if (onDemand && durationMs > 0L) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs), color = Color.White, fontSize = 12.sp)
                        Slider(scrubPosition, { scrubPosition = it; controls = true }, Modifier.weight(1f).padding(horizontal = 10.dp), onValueChangeFinished = { player.seekTo((scrubPosition * durationMs).toLong()) })
                        Text(formatTime(durationMs), color = Color.White, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (type == PlaybackType.LIVE) IconButton({ /* previous channel foundation */ }) { Icon(Icons.Default.SkipPrevious, "Previous channel", tint = Color.White) }
                if (onDemand) IconButton({ player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White) }
                FilledIconButton({ if (player.isPlaying) player.pause() else player.play() }, Modifier.size(64.dp)) { Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", Modifier.size(36.dp)) }
                if (onDemand) IconButton({ player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White) }
                if (type == PlaybackType.LIVE) IconButton({ /* next channel foundation */ }) { Icon(Icons.Default.SkipNext, "Next channel", tint = Color.White) }
                IconButton({ resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) { Icon(Icons.Default.AspectRatio, "Fit or fill", tint = Color.White) }
                IconButton({ controls = false; enterPip(pipAspectRatio) }) { Icon(Icons.Default.PictureInPictureAlt, "Picture in Picture", tint = Color.White) }
                }
            }
        } }
        if (!inPip) error?.let { message -> Card(Modifier.align(Alignment.Center).padding(24.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp)); Text(message, style = MaterialTheme.typography.titleMedium); Button({ candidateIndex = 0; attempt++; error = null }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Retry") } } } }
    }
}

@Composable private fun MiniEpg(programmes: List<GuideProgramme>, loading: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDescription by remember(programmes.firstOrNull()?.title) { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (isActive) { now = System.currentTimeMillis(); delay(1_000) } }
    val current = programmes.firstOrNull { it.startMillis != null && it.endMillis != null && it.startMillis <= now && it.endMillis > now }
        ?: programmes.firstOrNull { it.startMillis != null && it.startMillis > now }
    val next = current?.let { programme -> programmes.firstOrNull { it.startMillis != null && programme.endMillis != null && it.startMillis >= programme.endMillis } }
    if (!loading && current == null) return
    Surface(Modifier.fillMaxWidth().clickable(enabled = !loading) { showDescription = !showDescription }, shape = MaterialTheme.shapes.large, color = Color(0xE61A1D29)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LiveTv, null, Modifier.size(18.dp), tint = Color(0xFF9EB4FF))
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "Loading programme guide…" else "Now", color = Color(0xFF9EB4FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelLarge)
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(current?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (showDescription) {
                    Text(
                        current?.description?.takeIf { it.isNotBlank() } ?: "No programme description is available from this guide.",
                        color = Color.White.copy(alpha = .82f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4
                    )
                }
                val progress = current?.let { item ->
                    if (item.startMillis != null && item.endMillis != null && item.endMillis > item.startMillis) ((now - item.startMillis).toFloat() / (item.endMillis - item.startMillis)).coerceIn(0f, 1f) else 0f
                } ?: 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF8FAAFF), trackColor = Color.White.copy(alpha = .2f))
                next?.let { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Next", color = Color(0xFF9EB4FF), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(item.title, Modifier.weight(1f), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        item.startMillis?.let { start -> Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(start)), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    val hours = seconds / 3_600
    return if (hours > 0) "%d:%02d:%02d".format(hours, (seconds % 3_600) / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun String.guessMimeType(): String? = substringBefore('?').lowercase().let { path -> when {
    path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
    path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
    path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
    else -> null
} }
