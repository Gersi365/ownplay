package app.ownplay.player.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import app.ownplay.player.ui.shell.LocalTvHomeShellFocusBoundary
import app.ownplay.player.ui.tv.TvActionSurface
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import kotlinx.coroutines.flow.flowOf

private const val HOME_ROW_LIMIT = 20
private const val HOME_CONTINUE_WATCHING_LIMIT = 20
private const val HOME_CONTINUE_MOVIE_PREFIX = "continue-movie:"
private const val HOME_CONTINUE_EPISODE_PREFIX = "continue-episode:"
private const val HOME_MOVIE_PREFIX = "movie:"
private const val HOME_SERIES_PREFIX = "series:"
private const val HOME_EMPTY_ACTION_FOCUS_KEY = "home-empty-action"
private val HomePosterWidth = 166.dp

private enum class TvHomeShelfKind {
    CONTINUE_WATCHING,
    MOVIES,
    SERIES,
}

private data class TvHomeFocusLocation(
    val shelf: TvHomeShelfKind,
    val rowIndex: Int,
    val itemIndex: Int,
)

private data class TvHomeHeroModel(
    val eyebrow: String,
    val title: String,
    val detail: String?,
    val posterUrl: String?,
    val progress: Float?,
)

private sealed interface TvHomeContinueItem {
    val key: String
    val title: String
    val posterUrl: String?
    val positionMs: Long?
    val durationMs: Long?
    val updatedAtEpochMillis: Long?

    data class Movie(val movie: VodMovie) : TvHomeContinueItem {
        override val key: String = homeContinueMovieFocusKey(movie.movieId)
        override val title: String = movie.name
        override val posterUrl: String? = movie.posterUrl
        override val positionMs: Long? = movie.positionMs
        override val durationMs: Long? = movie.durationMs
        override val updatedAtEpochMillis: Long? = movie.progressUpdatedAtEpochMillis
    }

    data class Episode(val episode: SeriesEpisode) : TvHomeContinueItem {
        override val key: String = homeContinueEpisodeFocusKey(episode.episodeId)
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
    returnFocusKey: String?,
    returnFocusGeneration: Int,
    onReturnFocusConsumed: () -> Unit,
    onOpenMovieDetails: (sourceId: String, movieId: String, focusKey: String) -> Unit,
    onOpenSeriesDetails: (sourceId: String, seriesId: String, focusKey: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shellFocusBoundary = LocalTvHomeShellFocusBoundary.current
    val vodRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val seriesRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }
    val homeListState = rememberLazyListState()
    val continueWatchingState = rememberLazyListState()
    val moviesState = rememberLazyListState()
    val seriesState = rememberLazyListState()
    val returnFocusRequester = remember { FocusRequester() }
    val contentEntryFocusRequester = remember { FocusRequester() }

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
    val hero = remember(continueWatching, movies, series) {
        resolveHomeHero(
            continueWatching = continueWatching,
            movies = movies,
            series = series,
        )
    }
    val contentEntryKey = remember(
        sourceId,
        sourceKind,
        refreshing,
        continueWatching,
        movies,
        series,
    ) {
        resolveHomeContentEntryKey(
            sourceId = sourceId,
            sourceKind = sourceKind,
            refreshing = refreshing,
            continueWatching = continueWatching,
            movies = movies,
            series = series,
        )
    }

    LaunchedEffect(
        returnFocusGeneration,
        returnFocusKey,
        continueWatching,
        movies,
        series,
    ) {
        if (returnFocusGeneration <= 0) return@LaunchedEffect
        val focusKey = returnFocusKey ?: return@LaunchedEffect
        val location = resolveHomeFocusLocation(
            focusKey = focusKey,
            continueWatching = continueWatching,
            movies = movies,
            series = series,
        )
        if (location == null) {
            onReturnFocusConsumed()
            return@LaunchedEffect
        }

        homeListState.scrollToItem(location.rowIndex)
        when (location.shelf) {
            TvHomeShelfKind.CONTINUE_WATCHING -> continueWatchingState.scrollToItem(location.itemIndex)
            TvHomeShelfKind.MOVIES -> moviesState.scrollToItem(location.itemIndex)
            TvHomeShelfKind.SERIES -> seriesState.scrollToItem(location.itemIndex)
        }
        withFrameNanos { }
        withFrameNanos { }
        returnFocusRequester.requestFocus()
        onReturnFocusConsumed()
    }

    LaunchedEffect(
        shellFocusBoundary.contentEntryGeneration,
        contentEntryKey,
        continueWatching,
        movies,
        series,
    ) {
        if (shellFocusBoundary.contentEntryGeneration <= 0) return@LaunchedEffect
        val focusKey = contentEntryKey ?: return@LaunchedEffect
        val location = resolveHomeFocusLocation(
            focusKey = focusKey,
            continueWatching = continueWatching,
            movies = movies,
            series = series,
        )
        if (location != null) {
            homeListState.scrollToItem(location.rowIndex)
            when (location.shelf) {
                TvHomeShelfKind.CONTINUE_WATCHING -> continueWatchingState.scrollToItem(location.itemIndex)
                TvHomeShelfKind.MOVIES -> moviesState.scrollToItem(location.itemIndex)
                TvHomeShelfKind.SERIES -> seriesState.scrollToItem(location.itemIndex)
            }
        }
        withFrameNanos { }
        withFrameNanos { }
        contentEntryFocusRequester.requestFocus()
    }

    when {
        sourceId == null -> TvHomeMessage(
            title = "No playlist configured",
            detail = "Add a playlist to see Movies and Series on Home.",
            actionLabel = "Open Settings",
            entryFocusRequester = contentEntryFocusRequester,
            onEntryLeft = shellFocusBoundary.requestRailFocus,
            onAction = onOpenSettings,
            modifier = modifier,
        )
        sourceKind != SourceKinds.XTREAM -> TvHomeMessage(
            title = "No on-demand catalog for this playlist",
            detail = "Home shows Movies and Series when the active playlist provides them.",
            actionLabel = "Open Settings",
            entryFocusRequester = contentEntryFocusRequester,
            onEntryLeft = shellFocusBoundary.requestRailFocus,
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
            entryFocusRequester = contentEntryFocusRequester,
            onEntryLeft = shellFocusBoundary.requestRailFocus,
            onAction = onOpenSettings,
            modifier = modifier,
        )
        else -> LazyColumn(
            state = homeListState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(key = "home-header") {
                TvHomeHero(model = hero)
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "continue-watching") {
                    TvHomeContinueWatchingRow(
                        items = continueWatching,
                        state = continueWatchingState,
                        returnFocusKey = returnFocusKey,
                        returnFocusRequester = returnFocusRequester,
                        contentEntryKey = contentEntryKey,
                        contentEntryFocusRequester = contentEntryFocusRequester,
                        onLeftBoundary = shellFocusBoundary.requestRailFocus,
                        onOpenMovieDetails = { movie, focusKey ->
                            onOpenMovieDetails(sourceId, movie.movieId, focusKey)
                        },
                        onOpenSeriesDetails = { episode, focusKey ->
                            onOpenSeriesDetails(sourceId, episode.seriesId, focusKey)
                        },
                    )
                }
            }

            if (movies.isNotEmpty()) {
                item(key = "movies") {
                    TvHomeMovieRow(
                        movies = movies,
                        state = moviesState,
                        returnFocusKey = returnFocusKey,
                        returnFocusRequester = returnFocusRequester,
                        contentEntryKey = contentEntryKey,
                        contentEntryFocusRequester = contentEntryFocusRequester,
                        onLeftBoundary = shellFocusBoundary.requestRailFocus,
                        onOpenMovieDetails = { movie, focusKey ->
                            onOpenMovieDetails(sourceId, movie.movieId, focusKey)
                        },
                    )
                }
            }

            if (series.isNotEmpty()) {
                item(key = "series") {
                    TvHomeSeriesRow(
                        series = series,
                        state = seriesState,
                        returnFocusKey = returnFocusKey,
                        returnFocusRequester = returnFocusRequester,
                        contentEntryKey = contentEntryKey,
                        contentEntryFocusRequester = contentEntryFocusRequester,
                        onLeftBoundary = shellFocusBoundary.requestRailFocus,
                        onOpenSeriesDetails = { item, focusKey ->
                            onOpenSeriesDetails(sourceId, item.seriesId, focusKey)
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
    state: LazyListState,
    returnFocusKey: String?,
    returnFocusRequester: FocusRequester,
    contentEntryKey: String?,
    contentEntryFocusRequester: FocusRequester,
    onLeftBoundary: () -> Unit,
    onOpenMovieDetails: (VodMovie, focusKey: String) -> Unit,
    onOpenSeriesDetails: (SeriesEpisode, focusKey: String) -> Unit,
) {
    TvHomeShelf(
        title = "Continue Watching",
        state = state,
    ) {
        itemsIndexed(items = items, key = { _, item -> item.key }) { index, item ->
            TvHomePosterCard(
                focusKey = item.key,
                returnFocusKey = returnFocusKey,
                returnFocusRequester = returnFocusRequester,
                isContentEntry = item.key == contentEntryKey,
                isLeftBoundary = index == 0,
                contentEntryFocusRequester = contentEntryFocusRequester,
                onLeftBoundary = onLeftBoundary,
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
                        is TvHomeContinueItem.Movie -> onOpenMovieDetails(item.movie, item.key)
                        is TvHomeContinueItem.Episode -> onOpenSeriesDetails(item.episode, item.key)
                    }
                },
            )
        }
    }
}

@Composable
private fun TvHomeMovieRow(
    movies: List<VodMovie>,
    state: LazyListState,
    returnFocusKey: String?,
    returnFocusRequester: FocusRequester,
    contentEntryKey: String?,
    contentEntryFocusRequester: FocusRequester,
    onLeftBoundary: () -> Unit,
    onOpenMovieDetails: (VodMovie, focusKey: String) -> Unit,
) {
    TvHomeShelf(
        title = "Movies",
        state = state,
    ) {
        itemsIndexed(items = movies, key = { _, movie -> movie.movieId }) { index, movie ->
            val focusKey = homeMovieFocusKey(movie.movieId)
            TvHomePosterCard(
                focusKey = focusKey,
                returnFocusKey = returnFocusKey,
                returnFocusRequester = returnFocusRequester,
                isContentEntry = focusKey == contentEntryKey,
                isLeftBoundary = index == 0,
                contentEntryFocusRequester = contentEntryFocusRequester,
                onLeftBoundary = onLeftBoundary,
                title = movie.name,
                posterUrl = movie.posterUrl,
                subtitle = movie.rating?.let { rating -> "Rating ${formatRating(rating)}" },
                onClick = { onOpenMovieDetails(movie, focusKey) },
            )
        }
    }
}

@Composable
private fun TvHomeSeriesRow(
    series: List<SeriesSummary>,
    state: LazyListState,
    returnFocusKey: String?,
    returnFocusRequester: FocusRequester,
    contentEntryKey: String?,
    contentEntryFocusRequester: FocusRequester,
    onLeftBoundary: () -> Unit,
    onOpenSeriesDetails: (SeriesSummary, focusKey: String) -> Unit,
) {
    TvHomeShelf(
        title = "Series",
        state = state,
    ) {
        itemsIndexed(items = series, key = { _, item -> item.seriesId }) { index, item ->
            val focusKey = homeSeriesFocusKey(item.seriesId)
            TvHomePosterCard(
                focusKey = focusKey,
                returnFocusKey = returnFocusKey,
                returnFocusRequester = returnFocusRequester,
                isContentEntry = focusKey == contentEntryKey,
                isLeftBoundary = index == 0,
                contentEntryFocusRequester = contentEntryFocusRequester,
                onLeftBoundary = onLeftBoundary,
                title = item.name,
                posterUrl = item.posterUrl,
                subtitle = item.rating?.let { rating -> "Rating ${formatRating(rating)}" },
                onClick = { onOpenSeriesDetails(item, focusKey) },
            )
        }
    }
}

@Composable
private fun TvHomeShelf(
    title: String,
    state: LazyListState,
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
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun TvHomeHero(model: TvHomeHeroModel?) {
    if (model == null) {
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
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "HOME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = model.eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                model.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                model.progress?.let { watched ->
                    LinearProgressIndicator(
                        progress = { watched },
                        modifier = Modifier.fillMaxWidth(0.68f),
                    )
                }
            }

            RemotePoster(
                url = model.posterUrl,
                title = model.title,
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(2f / 3f),
            )
        }
    }
}

private fun resolveHomeHero(
    continueWatching: List<TvHomeContinueItem>,
    movies: List<VodMovie>,
    series: List<SeriesSummary>,
): TvHomeHeroModel? {
    continueWatching.firstOrNull()?.let { item ->
        return when (item) {
            is TvHomeContinueItem.Movie -> TvHomeHeroModel(
                eyebrow = "CONTINUE WATCHING",
                title = item.movie.name,
                detail = item.movie.rating?.let { rating -> "Rating ${formatRating(rating)}" },
                posterUrl = item.movie.posterUrl,
                progress = progressFraction(item.movie.positionMs, item.movie.durationMs),
            )
            is TvHomeContinueItem.Episode -> TvHomeHeroModel(
                eyebrow = "CONTINUE WATCHING",
                title = item.episode.seriesTitle,
                detail = "S${item.episode.seasonNumber} · E${item.episode.episodeNumber} · ${item.episode.title}",
                posterUrl = item.episode.posterUrl,
                progress = progressFraction(item.episode.positionMs, item.episode.durationMs),
            )
        }
    }

    movies.firstOrNull()?.let { movie ->
        return TvHomeHeroModel(
            eyebrow = "MOVIE",
            title = movie.name,
            detail = movie.rating?.let { rating -> "Rating ${formatRating(rating)}" },
            posterUrl = movie.posterUrl,
            progress = null,
        )
    }

    series.firstOrNull()?.let { item ->
        return TvHomeHeroModel(
            eyebrow = "SERIES",
            title = item.name,
            detail = item.rating?.let { rating -> "Rating ${formatRating(rating)}" },
            posterUrl = item.posterUrl,
            progress = null,
        )
    }

    return null
}

private fun resolveHomeContentEntryKey(
    sourceId: String?,
    sourceKind: String?,
    refreshing: Boolean,
    continueWatching: List<TvHomeContinueItem>,
    movies: List<VodMovie>,
    series: List<SeriesSummary>,
): String? = when {
    sourceId == null -> HOME_EMPTY_ACTION_FOCUS_KEY
    sourceKind != SourceKinds.XTREAM -> HOME_EMPTY_ACTION_FOCUS_KEY
    refreshing && continueWatching.isEmpty() && movies.isEmpty() && series.isEmpty() -> null
    continueWatching.isNotEmpty() -> continueWatching.first().key
    movies.isNotEmpty() -> homeMovieFocusKey(movies.first().movieId)
    series.isNotEmpty() -> homeSeriesFocusKey(series.first().seriesId)
    else -> HOME_EMPTY_ACTION_FOCUS_KEY
}

@Composable
private fun TvHomePosterCard(
    focusKey: String,
    returnFocusKey: String?,
    returnFocusRequester: FocusRequester,
    isContentEntry: Boolean,
    isLeftBoundary: Boolean,
    contentEntryFocusRequester: FocusRequester,
    onLeftBoundary: () -> Unit,
    title: String,
    posterUrl: String?,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    var focused by remember(focusKey) { mutableStateOf(false) }
    val restoreModifier = if (focusKey == returnFocusKey) {
        Modifier.focusRequester(returnFocusRequester)
    } else {
        Modifier
    }
    val entryModifier = if (isContentEntry) {
        Modifier.focusRequester(contentEntryFocusRequester)
    } else {
        Modifier
    }
    val leftBoundaryModifier = if (isLeftBoundary) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                onLeftBoundary()
                true
            } else {
                false
            }
        }
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .width(HomePosterWidth)
            .then(restoreModifier)
            .then(entryModifier)
            .then(leftBoundaryModifier)
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

private fun resolveHomeFocusLocation(
    focusKey: String,
    continueWatching: List<TvHomeContinueItem>,
    movies: List<VodMovie>,
    series: List<SeriesSummary>,
): TvHomeFocusLocation? {
    var rowIndex = 1

    if (continueWatching.isNotEmpty()) {
        val exactIndex = continueWatching.indexOfFirst { item -> item.key == focusKey }
        if (exactIndex >= 0) {
            return TvHomeFocusLocation(
                shelf = TvHomeShelfKind.CONTINUE_WATCHING,
                rowIndex = rowIndex,
                itemIndex = exactIndex,
            )
        }
        if (
            focusKey.startsWith(HOME_CONTINUE_MOVIE_PREFIX) ||
            focusKey.startsWith(HOME_CONTINUE_EPISODE_PREFIX)
        ) {
            return TvHomeFocusLocation(TvHomeShelfKind.CONTINUE_WATCHING, rowIndex, 0)
        }
        rowIndex += 1
    }

    if (movies.isNotEmpty()) {
        val exactIndex = movies.indexOfFirst { movie -> homeMovieFocusKey(movie.movieId) == focusKey }
        if (exactIndex >= 0) {
            return TvHomeFocusLocation(TvHomeShelfKind.MOVIES, rowIndex, exactIndex)
        }
        if (focusKey.startsWith(HOME_MOVIE_PREFIX)) {
            return TvHomeFocusLocation(TvHomeShelfKind.MOVIES, rowIndex, 0)
        }
        rowIndex += 1
    }

    if (series.isNotEmpty()) {
        val exactIndex = series.indexOfFirst { item -> homeSeriesFocusKey(item.seriesId) == focusKey }
        if (exactIndex >= 0) {
            return TvHomeFocusLocation(TvHomeShelfKind.SERIES, rowIndex, exactIndex)
        }
        if (focusKey.startsWith(HOME_SERIES_PREFIX)) {
            return TvHomeFocusLocation(TvHomeShelfKind.SERIES, rowIndex, 0)
        }
    }

    return when {
        continueWatching.isNotEmpty() -> TvHomeFocusLocation(TvHomeShelfKind.CONTINUE_WATCHING, 1, 0)
        movies.isNotEmpty() -> TvHomeFocusLocation(TvHomeShelfKind.MOVIES, 1, 0)
        series.isNotEmpty() -> TvHomeFocusLocation(TvHomeShelfKind.SERIES, 1, 0)
        else -> null
    }
}

private fun homeContinueMovieFocusKey(movieId: String): String =
    "$HOME_CONTINUE_MOVIE_PREFIX$movieId"

private fun homeContinueEpisodeFocusKey(episodeId: String): String =
    "$HOME_CONTINUE_EPISODE_PREFIX$episodeId"

private fun homeMovieFocusKey(movieId: String): String = "$HOME_MOVIE_PREFIX$movieId"

private fun homeSeriesFocusKey(seriesId: String): String = "$HOME_SERIES_PREFIX$seriesId"

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
    entryFocusRequester: FocusRequester,
    onEntryLeft: () -> Unit,
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
        TvActionSurface(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier
                .focusRequester(entryFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onEntryLeft()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

private fun formatRating(rating: Double): String = String.format("%.1f", rating)
