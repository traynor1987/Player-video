@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.traynor.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.traynor.player.AppContainer
import com.traynor.player.data.repository.FootballListing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date

private val suggestedTeams = listOf(
    "Liverpool", "Everton", "Manchester United", "Manchester City", "Arsenal", "Chelsea",
    "Newcastle United", "Tottenham Hotspur", "Aston Villa", "Leeds United", "Sunderland", "England"
)

data class FootballUiState(
    val selectedTeams: Set<String> = emptySet(),
    val saturday: LocalDate = upcomingSaturday(),
    val loading: Boolean = false,
    val checked: Boolean = false,
    val listings: List<FootballListing> = emptyList(),
    val message: String? = null
)

class FootballViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(FootballUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch { mutable.value = mutable.value.copy(selectedTeams = container.preferences.footballTeams.first()) }
    }

    fun toggleTeam(team: String) = viewModelScope.launch {
        val selected = mutable.value.selectedTeams.toMutableSet()
        if (!selected.add(team)) selected.remove(team)
        container.preferences.setFootballTeams(selected)
        mutable.value = mutable.value.copy(selectedTeams = selected, checked = false, listings = emptyList(), message = null)
    }

    fun checkSaturday() = viewModelScope.launch {
        val selected = mutable.value.selectedTeams
        if (selected.isEmpty()) {
            mutable.value = mutable.value.copy(checked = true, message = "Choose at least one team first.")
            return@launch
        }
        val date = mutable.value.saturday
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sourceId = container.preferences.activeSourceId.first()
        if (sourceId == null) {
            mutable.value = mutable.value.copy(checked = true, message = "Choose a TV source before checking fixtures.")
            return@launch
        }
        mutable.value = mutable.value.copy(loading = true, checked = false, listings = emptyList(), message = "Checking the sports guide in your active source…")
        val listings = runCatching { container.sourceRepository.footballListings(sourceId, selected, start, end) }.getOrElse { emptyList() }
        mutable.value = mutable.value.copy(
            loading = false,
            checked = true,
            listings = listings,
            message = if (listings.isEmpty()) "No matching fixture was supplied in this source's Saturday guide." else null
        )
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = FootballViewModel(container) as T
        }
    }
}

@Composable
fun FootballScreen(container: AppContainer, watch: (Long) -> Unit) {
    val model: FootballViewModel = viewModel(factory = FootballViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsSoccer, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Football", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Saturday matches from your TV guide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Follow your teams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Player searches only the EPG from your active, legitimate source. It never searches for streams.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestedTeams.forEach { team ->
                            FilterChip(selected = team in state.selectedTeams, onClick = { model.toggleTeam(team) }, label = { Text(team) })
                        }
                    }
                    Button(onClick = model::checkSaturday, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.loading) "Checking Saturday…" else "Check ${state.saturday.formatShort()}")
                    }
                }
            }
        }
        state.message?.let { message -> item { Text(message, color = if (state.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) } }
        if (state.checked && state.listings.isNotEmpty()) {
            item { Text("On your TV source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.listings, key = { "${it.channelId}:${it.programme.startMillis}:${it.programme.title}" }) { listing ->
                FixtureCard(listing, watch)
            }
        }
    }
}

@Composable
private fun FixtureCard(listing: FootballListing, watch: (Long) -> Unit) {
    val kickoff = listing.programme.startMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        ?: listing.programme.startLabel ?: "Kick-off time unavailable"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(kickoff, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(listing.programme.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listing.channelName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listing.programme.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = { watch(listing.channelId) }) { Icon(Icons.Default.PlayArrow, "Watch channel") }
        }
    }
}

private fun upcomingSaturday(): LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
private fun LocalDate.formatShort(): String = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(this)
