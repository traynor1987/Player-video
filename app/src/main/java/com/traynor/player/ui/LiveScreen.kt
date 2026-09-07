package com.traynor.player.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.traynor.player.AppContainer
import com.traynor.player.data.local.ChannelEntity

@Composable fun LiveScreen(container: AppContainer, play: (Long) -> Unit, openGuide: () -> Unit) {
    val model: LiveViewModel = viewModel(factory = LiveViewModel.factory(container)); val state by model.state.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (wide) Row(Modifier.fillMaxSize()) {
            CategoryPane(state, model, openGuide, Modifier.width(230.dp).fillMaxHeight())
            ChannelPane(state, model, play, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.widthIn(min = 300.dp, max = 440.dp).fillMaxHeight().padding(20.dp), contentAlignment = Alignment.Center) { Text("Choose a channel to play\nFull-screen playback supports remote controls and Picture-in-Picture.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else Column(Modifier.fillMaxSize()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(openGuide, label = { Text("TV Guide") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null) }) }
                item { FilterChip(state.selectedCategory == null, { model.selectCategory(null) }, { Text("All") }) }
                items(state.categories) { category -> FilterChip(state.selectedCategory == category, { model.selectCategory(category) }, { Text(category) }) }
            }
            ChannelPane(state, model, play, Modifier.weight(1f))
        }
    }
}

@Composable private fun CategoryPane(state: LiveUiState, model: LiveViewModel, openGuide: () -> Unit, modifier: Modifier) = LazyColumn(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    item { Text("Live TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp)) }
    item { FilledTonalButton(openGuide, Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("TV Guide") } }
    item { CategoryButton("All channels", state.selectedCategory == null) { model.selectCategory(null) } }
    items(state.categories) { category -> CategoryButton(category, state.selectedCategory == category) { model.selectCategory(category) } }
}

@Composable private fun CategoryButton(text: String, selected: Boolean, action: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = action, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))) { Text(text, Modifier.padding(14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable private fun ChannelPane(state: LiveUiState, model: LiveViewModel, play: (Long) -> Unit, modifier: Modifier) = Column(modifier.padding(14.dp)) {
    OutlinedTextField(state.query, model::search, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search channels") }, singleLine = true)
    Spacer(Modifier.height(12.dp))
    if (state.channels.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LiveTv, null, Modifier.size(52.dp)); Text(if (state.query.isBlank()) "This category is empty" else "No matching channels") } }
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(state.channels, key = { it.id }) { channel -> ChannelRow(channel) { model.remember(channel.id); play(channel.id) } } }
}

@Composable private fun ChannelRow(channel: ChannelEntity, play: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    ElevatedCard(onClick = play, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(64.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { if (!channel.logoUrl.isNullOrBlank()) AsyncImage(channel.logoUrl, channel.name, Modifier.padding(6.dp)) else Icon(Icons.Default.LiveTv, null, Modifier.padding(16.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(channel.category, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1); Text("Programme information unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton({ /* favourite wiring uses the local foundation */ }) { Icon(Icons.Default.FavoriteBorder, "Favourite") }; Icon(Icons.Default.PlayArrow, null)
        }
    }
}
