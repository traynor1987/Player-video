package com.traynor.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.traynor.player.AppContainer
import com.traynor.player.data.local.ChannelEntity
import com.traynor.player.data.repository.GuideProgramme
import java.text.DateFormat
import java.util.Date

@Composable fun GuideScreen(container: AppContainer, play: (Long) -> Unit) {
    val model: GuideViewModel = viewModel(factory = GuideViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.selectedChannel?.id, state.channels.firstOrNull()?.id) {
        if (state.selectedChannel == null) state.channels.firstOrNull()?.let { model.select(it.id) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (state.channels.isEmpty()) GuideEmpty("No live channels are available from this source.")
        else if (wide) Row(Modifier.fillMaxSize()) {
            ChannelPicker(state.channels, state.selectedChannel?.id, model::select, Modifier.width(330.dp).fillMaxHeight())
            ProgrammePane(state, play, Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            GuideChannelStrip(state.channels, state.selectedChannel?.id, model::select)
            ProgrammePane(state, play, Modifier.weight(1f))
        }
    }
}

@Composable private fun GuideChannelStrip(channels: List<ChannelEntity>, selected: Long?, select: (Long) -> Unit) = LazyRow(
    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
) {
    item { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) }
    item { Text("TV Guide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    items(channels, key = { it.id }) { channel ->
        FilterChip(selected = channel.id == selected, onClick = { select(channel.id) }, label = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
    }
}

@Composable private fun ChannelPicker(channels: List<ChannelEntity>, selected: Long?, select: (Long) -> Unit, modifier: Modifier) = LazyColumn(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    item { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("TV Guide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
    items(channels, key = { it.id }) { channel ->
        val isSelected = channel.id == selected
        Surface(onClick = { select(channel.id) }, color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(channel.name, Modifier.padding(13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable private fun ProgrammePane(state: GuideUiState, play: (Long) -> Unit, modifier: Modifier) = when {
    state.selectedChannel == null -> GuideEmpty("Choose a channel to load its guide.", modifier)
    state.loadingProgrammes -> Box(modifier, contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Loading guide from your TV source…") } }
    state.programmes.isEmpty() -> GuideEmpty("This provider has not supplied guide data for ${state.selectedChannel.name}.", modifier, state.selectedChannel.id, play)
    else -> LazyColumn(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LiveTv, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(state.selectedChannel.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Programme guide from your Xtream source", color = MaterialTheme.colorScheme.onSurfaceVariant) }; FilledTonalButton({ play(state.selectedChannel.id) }) { Icon(Icons.Default.PlayArrow, null); Text(" Watch") } } }
        items(state.programmes) { programme -> ProgrammeCard(programme) }
    }
}

@Composable private fun ProgrammeCard(programme: GuideProgramme) {
    val time = listOfNotNull(programme.startMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: programme.startLabel, programme.endMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: programme.endLabel).joinToString(" – ")
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { if (time.isNotBlank()) Text(time, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Text(programme.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); programme.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}

@Composable private fun GuideEmpty(message: String, modifier: Modifier = Modifier.fillMaxSize(), channelId: Long? = null, play: ((Long) -> Unit)? = null) = Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); if (channelId != null && play != null) Button({ play(channelId) }) { Icon(Icons.Default.PlayArrow, null); Text(" Watch channel") } } }
