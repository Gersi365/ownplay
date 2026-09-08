package app.ownplay.player.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.ui.library.progressFraction
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import kotlinx.coroutines.flow.flowOf

private const val HOME_ROW_LIMIT = 20
private const val HOME_CONTINUE_WATCHING_LIMIT = 20
private val HomePosterWidth = 166.dp

private sealed interface TvHomeContinueItem {
    val key: String
    val title: String
    val posterUrl: String?
    val positionMs: Long?
    val durationMs: Long?
    val updatedAtEpochMillis: Long?

    data class Movie(val movie: VodMovie) : TvHomeContinueItem {
        override val key: String = "movie:${movie.movieId}"
        override val title: String = movie.name
        override val posterUrl: String? = movie.posterUrl
        override val positionMs: Long? = movie.positionMs
        override val durationMs: Long? = movie.durationMs
        override val updatedAtEpochMillis: Long? = movie.progressUpdatedAtEpochMillis
    }

    data class Episode(val episode: SeriesEpisode) : TvHomeContinueItem {
        override val key: String = "episode:${episode.episodeId}"
        override val title: String = episode.seriesTitle
        override val posterUrl: String? = episode.posterUrl
        override val positionMs: Long? = episode.positionMs
        override val durationMs: Long? = episode.durationMs
        override val updatedAtEpochMillis: Long? = episode.progressUpdatedAtEpochMillis
    }
}

@Composable
internal fun TvHomeScreen(
    sourceId: String?,
    sourceKind: String?,
    onOpenMovieDetails: (sourceId: String, movieId: String) -> Unit,
    onOpenSeriesDetails: (sourceId: String, seriesId: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vodRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val seriesRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }

    DisposableEffect(vodRuntime, seriesRuntime) {
        onDispose {
            vodRuntime.close()
            seriesRuntime.close()
        }
    }

    val vodFlow = remember(sourceId, vodRuntime) {
        sourceId?.let(vodRuntime::observeCatalog) ?: flowOf(VodCatalog())
    }
    val seriesFlow = remember(sourceId, seriesRuntime) {
        sourceId?.let(seriesRuntime::observeCatalog) ?: flowOf(SeriesCatalog())
    }
    val vodCatalog by vodFlow.collectAsState(initial = VodCatalog())
    val seriesCatalog by seriesFlow.collectAsState(initial = SeriesCatalog())

    var movieRefreshRunning by remember(sourceId, sourceKind) { mutableStateOf(false) }
    var seriesRefreshRunning by remember(sourceId, sourceKind) { mutableStateOf(false) }

    LaunchedEffect(sourceId, sourceKind, vodRuntime) {
        val resolvedSourceId = sourceId
        if (resolvedSourceId == null || sourceKind != SourceKinds.XTREAM) return@LaunchedEffect
        movieRefreshRunning = true
        try {
            vodRuntime.refresh(resolvedSourceId)
        } finally {
            movieRefreshRunning = false
        }
    }

    LaunchedEffect(sourceId, sourceKind, seriesRuntime) {
        val resolvedSourceId = sourceId
        if (resolvedSourceId == null || sourceKind != SourceKinds.XTREAM) return@LaunchedEffect
        seriesRefreshRunning = true
        try {
            seriesRuntime.refresh(resolvedSourceId)
        } finally {
            seriesRefreshRunning = false
        }
    }

    val continueWatching = remember(vodCatalog.continueWatching, seriesCatalog.continueWatching) {
        buildList<TvHomeContinueItem> {
            vodCatalog.continueWatching.forEach { add(TvHomeContinueItem.Movie(it)) }
            seriesCatalog.continueWatching.forEach { add(TvHomeContinueItem.Episode(it)) }
        }
            .sortedByDescending { item -> item.updatedAtEpochMillis ?: 0L }
            .take(HOME_CONTINUE_WATCHING_LIMIT)
    }
    val movies = remember(vodCatalog.movies) { vodCatalog.movies.take(HOME_ROW_LIMIT) }
    val series = remember(seriesCatalog.series) { seriesCatalog.series.take(HOME_ROW_LIMIT) }
    val refreshing = movieRefreshRunning || seriesRefreshRunning

    when {
        sourceId == null -> TvHomeMessage(
            title = "No playlist configured",
            detail = "Add a playlist to see Movies and Series on Home.",
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
            modifier = modifier,
        )
        sourceKind != SourceKinds.XTREAM -> TvHomeMessage(
            title = "No on-demand catalog for this playlist",
            detail = "Home shows Movies and Series when the active playlist provides them.",
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
            modifier = modifier,
        )
        refreshing && movies.isEmpty() && series.isEmpty() && continueWatching.isEmpty() -> TvHomeLoading(
            modifier = modifier,
        )
        movies.isEmpty() && series.isEmpty() && continueWatching.isEmpty() -> TvHomeMessage(
            title = "No Movies or Series found",
            detail = "The active playlist does not currently provide on-demand content.",
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(key = "home-header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Your Movies and Series",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "continue-watching") {
                    TvHomeContinueWatchingRow(
                        items = continueWatching,
                        onOpenMovieDetails = { movie ->
                            onOpenMovieDetails(sourceId, movie.movieId)
                        },
                        onOpenSeriesDetails = { episode ->
                            onOpenSeriesDetails(sourceId, episode.seriesId)
                        },
                    )
                }
            }

            if (movies.isNotEmpty()) {
                item(key = "movies") {
                    TvHomeMovieRow(
                        movies = movies,
                        onOpenMovieDetails = { movie ->
                            onOpenMovieDetails(sourceId, movie.movieId)
                        },
                    )
                }
            }

            if (series.isNotEmpty()) {
                item(key = "series") {
                    TvHomeSeriesRow(
                        series = series,
                        onOpenSeriesDetails = { item ->
                            onOpenSeriesDetails(sourceId, item.seriesId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvHomeContinueWatchingRow(
    items: List<TvHomeContinueItem>,
    onOpenMovieDetails: (VodMovie) -> Unit,
    onOpenSeriesDetails: (SeriesEpisode) -> Unit,
) {
    TvHomeShelf(title = "Continue Watching") {
        items(items = items, key = { it.key }) { item ->
            TvHomePosterCard(
                title = item.title,
                posterUrl = item.posterUrl,
                subtitle = when (item) {
                    is TvHomeContinueItem.Movie -> "Movie"
                    is TvHomeContinueItem.Episode ->
                        "S${item.episode.seasonNumber} · E${item.episode.episodeNumber} · ${item.episode.title}"
                },
                progress = progressFraction(item.positionMs, item.durationMs),
                onClick = {
                    when (item) {
                        is TvHomeContinueItem.Movie -> onOpenMovieDetails(item.movie)
                        is TvHomeContinueItem.Episode -> onOpenSeriesDetails(item.episode)
                    }
                },
            )
        }
    }
}

@Composable
private fun TvHomeMovieRow(
    movies: List<VodMovie>,
    onOpenMovieDetails: (VodMovie) -> Unit,
) {
    TvHomeShelf(title = "Movies") {
        items(items = movies, key = { it.movieId }) { movie ->
            TvHomePosterCard(
                title = movie.name,
                posterUrl = movie.posterUrl,
                subtitle = movie.rating?.let { rating -> "Rating ${formatRating(rating)}" },
                onClick = { onOpenMovieDetails(movie) },
            )
        }
    }
}

@Composable
private fun TvHomeSeriesRow(
    series: List<SeriesSummary>,
    onOpenSeriesDetails: (SeriesSummary) -> Unit,
) {
    TvHomeShelf(title = "Series") {
        items(items = series, key = { it.seriesId }) { item ->
            TvHomePosterCard(
                title = item.name,
                posterUrl = item.posterUrl,
                subtitle = item.rating?.let { rating -> "Rating ${formatRating(rating)}" },
                onClick = { onOpenSeriesDetails(item) },
            )
        }
    }
}

@Composable
private fun TvHomeShelf(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun TvHomePosterCard(
    title: String,
    posterUrl: String?,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    var focused by remember(title, posterUrl) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .width(HomePosterWidth)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            RemotePoster(
                url = posterUrl,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            progress?.let { watched ->
                LinearProgressIndicator(
                    progress = { watched },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvHomeLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading Movies and Series…",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TvHomeMessage(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun formatRating(rating: Double): String = String.format("%.1f", rating)
