package com.traynor.player.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import com.traynor.player.AppContainer
import com.traynor.player.data.local.MovieEntity
import com.traynor.player.data.local.SeriesEntity
import com.traynor.player.data.repository.SeriesEpisode

@Composable fun MoviesScreen(container: AppContainer, openMovie: (Long) -> Unit) {
    val model: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(container)); val state by model.state.collectAsStateWithLifecycle()
    LibraryBrowser(
        title = "Movies", categories = state.categories, selectedCategory = state.selectedCategory, query = state.query,
        loading = state.loading, isEmpty = state.movies.isEmpty(), emptyMessage = "No movies have been imported yet. Refresh your source in Settings.",
        selectCategory = model::selectCategory, search = model::search
    ) {
        items(state.movies, key = { it.id }) { MoviePoster(it, { openMovie(it.id) }) }
    }
}

@Composable fun SeriesScreen(container: AppContainer, openSeries: (Long) -> Unit) {
    val model: SeriesViewModel = viewModel(factory = SeriesViewModel.factory(container)); val state by model.state.collectAsStateWithLifecycle()
    LibraryBrowser(
        title = "Series", categories = state.categories, selectedCategory = state.selectedCategory, query = state.query,
        loading = state.loading, isEmpty = state.series.isEmpty(), emptyMessage = "No series have been imported yet. Refresh your source in Settings.",
        selectCategory = model::selectCategory, search = model::search
    ) {
        items(state.series, key = { it.id }) { SeriesPoster(it, { openSeries(it.id) }) }
    }
}

@Composable private fun LibraryBrowser(
    title: String, categories: List<String>, selectedCategory: String?, query: String, loading: Boolean, isEmpty: Boolean, emptyMessage: String,
    selectCategory: (String?) -> Unit, search: (String) -> Unit,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit
) = Column(Modifier.fillMaxSize().padding(18.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(query, search, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search $title") }, leadingIcon = { Icon(Icons.Default.Search, null) })
    LazyRow(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selectedCategory == null, { selectCategory(null) }, { Text("All") }) }
        items(categories) { category -> FilterChip(selectedCategory == category, { selectCategory(category) }, { Text(category) }) }
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable private fun MoviePoster(item: MovieEntity, open: () -> Unit) = PosterCard(item.title, item.posterUrl, item.year, open)
@Composable private fun SeriesPoster(item: SeriesEntity, open: () -> Unit) = PosterCard(item.title, item.posterUrl, item.year, open)

@Composable private fun PosterCard(title: String, artwork: String?, year: String?, open: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
        Column {
            Surface(Modifier.fillMaxWidth().height(185.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                if (!artwork.isNullOrBlank()) AsyncImage(artwork, title, Modifier.fillMaxSize()) else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, null, Modifier.size(38.dp)) }
            }
            Column(Modifier.padding(10.dp)) { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); year?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable fun MovieDetailScreen(movieId: Long, container: AppContainer, play: (Long) -> Unit, close: () -> Unit) {
    var movie by remember { mutableStateOf<MovieEntity?>(null) }
    val model: MovieDetailViewModel = viewModel(factory = MovieDetailViewModel.factory(container)); val detailState by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(movieId) { movie = container.database.movieDao().get(movieId) }
    LaunchedEffect(movieId) { model.load(movieId) }
    val item = movie ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    val detail = detailState.details
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(close) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Movies") } }
        item { Row(verticalAlignment = Alignment.Top) { Surface(Modifier.width(150.dp).height(220.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) { if (!item.posterUrl.isNullOrBlank()) AsyncImage(item.posterUrl, item.title, Modifier.fillMaxSize()) else Icon(Icons.Default.Movie, null, Modifier.padding(46.dp)) }; Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(listOfNotNull(detail?.year ?: item.year, (detail?.rating ?: item.rating)?.let { "$it ★" }, detail?.runtime ?: item.runtime).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant); Button({ play(item.id) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play") } } } }
        item { Text("Synopsis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); if (detailState.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text((detail?.synopsis ?: item.description)?.takeIf { it.isNotBlank() } ?: "No synopsis was supplied by this source.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text("Where to watch in the UK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
            when {
                !detailState.availabilityConfigured -> Text("Add your TMDB API key in Settings to check legal streaming, rental and purchase availability.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                detailState.availability == null -> Text("No UK availability was found right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> AvailabilityDetails(detailState.availability)
            }
            Spacer(Modifier.height(8.dp)); Text("Rotten Tomatoes critic and audience scores require a licensed Rotten Tomatoes data feed; they are not scraped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun AvailabilityDetails(availability: com.traynor.player.data.network.UkAvailability) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (availability.subscriptions.isNotEmpty()) Text("Stream: ${availability.subscriptions.joinToString()}")
    if (availability.rent.isNotEmpty()) Text("Rent: ${availability.rent.joinToString()}")
    if (availability.buy.isNotEmpty()) Text("Buy: ${availability.buy.joinToString()}")
    if (availability.subscriptions.isEmpty() && availability.rent.isEmpty() && availability.buy.isEmpty()) Text("No listed legal providers in the UK right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun SeriesDetailScreen(seriesId: Long, container: AppContainer, play: (SeriesEpisode) -> Unit, close: () -> Unit) {
    val model: SeriesDetailViewModel = viewModel(factory = SeriesDetailViewModel.factory(container)); val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(seriesId) { model.load(seriesId) }
    val show = state.series
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(close) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Series") } }
        show?.let { series -> item { Row(verticalAlignment = Alignment.Top) { Surface(Modifier.width(140.dp).height(205.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) { if (!series.posterUrl.isNullOrBlank()) AsyncImage(series.posterUrl, series.title, Modifier.fillMaxSize()) else Icon(Icons.Default.VideoLibrary, null, Modifier.padding(42.dp)) }; Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(series.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(listOfNotNull(series.year, series.rating?.let { "$it ★" }).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant); series.description?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(8.dp)); Text(it) } } } } }
        when {
            state.loading -> item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.error -> item { Text("Episodes are unavailable from this source right now.", color = MaterialTheme.colorScheme.error) }
            state.episodes.isEmpty() -> item { Text("No episodes were supplied for this series.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> {
                item { Text("Episodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(state.episodes, key = { "${it.season}-${it.episodeId}" }) { episode -> EpisodeRow(episode) { play(episode) } }
            }
        }
    }
}

@Composable private fun EpisodeRow(episode: SeriesEpisode, play: () -> Unit) = ElevatedCard(onClick = play, Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(54.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { if (!episode.artworkUrl.isNullOrBlank()) AsyncImage(episode.artworkUrl, episode.title, Modifier.fillMaxSize()) else Icon(Icons.Default.PlayArrow, null, Modifier.padding(14.dp)) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("S${episode.season} • E${episode.number ?: "–"}  ${episode.title}", fontWeight = FontWeight.SemiBold); episode.description?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Icon(Icons.Default.PlayArrow, null)
    }
}
