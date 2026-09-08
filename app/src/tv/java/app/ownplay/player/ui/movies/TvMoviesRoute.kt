package app.ownplay.player.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.onDemandPresentationSession
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.playback.OnDemandContentKind
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import app.ownplay.player.vod.VodMovieDetails
import kotlinx.coroutines.launch

private const val MOVIES_CONTINUE_KEY = "movies:continue"
private const val MOVIES_ALL_KEY = "movies:all"
private const val MOVIES_FAVORITES_KEY = "movies:favorites"
private const val MOVIES_CATEGORY_PREFIX = "movies:category:"
private const val MOVIE_GRID_COLUMNS = 5

private data class TvMovieSection(
    val key: String,
    val label: String,
)

/**
 * TV-only Movies catalog and details presentation.
 *
 * Repository, provider, progress and playback ownership stay in the established shared runtimes.
 * This route owns only TV browse/detail geometry and remote focus restoration. Downloads/Offline
 * management are intentionally absent from the TV product surface.
 */
@Composable
internal fun TvMoviesRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    requestedMovieId: String? = null,
    onRequestedMovieConsumed: () -> Unit = {},
    returnToLibraryOnDetailBack: Boolean = false,
    onReturnToLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val featureRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val onDemandPresentation by runtime.onDemandPresentationSession.state.collectAsState()

    DisposableEffect(featureRuntime) {
        onDispose { featureRuntime.close() }
    }

    if (sourceId == null) {
        TvMoviesMessage(
            title = "No playlist configured",
            detail = "Add an Xtream playlist from Settings to load Movies.",
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    if (sourceKind != SourceKinds.XTREAM) {
        TvMoviesMessage(
            title = "Movies are not available for this playlist",
            detail = "The active playlist does not currently provide an Xtream Movies catalog.",
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    val catalog by featureRuntime.observeCatalog(sourceId).collectAsState(initial = VodCatalog())
    var loading by remember(sourceId) { mutableStateOf(false) }
    var refreshError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var selectedSectionKey by remember(sourceId) { mutableStateOf(MOVIES_ALL_KEY) }
    var selectedMovie by remember(sourceId) { mutableStateOf<VodMovie?>(null) }
    var details by remember(sourceId) { mutableStateOf<VodMovieDetails?>(null) }
    var detailsLoading by remember(sourceId) { mutableStateOf(false) }
    var detailsError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var movieFocusTargetId by remember(sourceId) { mutableStateOf<String?>(null) }
    var movieFocusRequestGeneration by remember(sourceId) { mutableIntStateOf(0) }
    val detailsBackOwner = remember(sourceId) { Any() }

    fun refreshCatalog() {
        scope.launch {
            loading = true
            refreshError = null
            when (val result = featureRuntime.refresh(sourceId)) {
                is SourceResult.Success -> Unit
                is SourceResult.Failure -> refreshError = result.error
            }
            loading = false
        }
    }

    fun openMovie(movie: VodMovie) {
        selectedMovie = movie
        runtime.onDemandPresentationSession.showMovieDetail(
            sourceId = sourceId,
            movieId = movie.movieId,
            returnToLibraryOnDetailBack = returnToLibraryOnDetailBack,
        )
    }

    fun closeMovieDetails() {
        val originMovieId = selectedMovie?.movieId
        if (returnToLibraryOnDetailBack) {
            runtime.onDemandPresentationSession.clear()
            onReturnToLibrary()
            return
        }
        selectedMovie = null
        runtime.onDemandPresentationSession.showMovieCatalog(sourceId)
        if (originMovieId != null) {
            movieFocusTargetId = originMovieId
            movieFocusRequestGeneration += 1
        }
    }

    fun startMovie(movie: VodMovie, fromBeginning: Boolean) {
        val playbackMovie = if (fromBeginning) {
            movie.copy(
                positionMs = null,
                progressCompleted = false,
            )
        } else {
            movie
        }
        runtime.playbackController.start(
            PlaybackRequest(
                sourceId = sourceId,
                channelId = movie.movieId,
                mediaKind = PlaybackMediaKind.MOVIE,
            ),
        )
        runtime.onDemandPresentationSession.showMoviePlayback(
            sourceId = sourceId,
            movie = playbackMovie,
            returnToLibraryOnDetailBack = returnToLibraryOnDetailBack,
        )
    }

    fun setMovieFavorite(movie: VodMovie, favorite: Boolean) {
        scope.launch {
            if (!featureRuntime.setFavorite(sourceId, movie.movieId, favorite)) return@launch
            selectedMovie = selectedMovie?.let { current ->
                if (current.movieId == movie.movieId) current.copy(isFavorite = favorite) else current
            }
            details = details?.let { current ->
                if (current.movie.movieId == movie.movieId) {
                    current.copy(movie = current.movie.copy(isFavorite = favorite))
                } else {
                    current
                }
            }
        }
    }

    DisposableEffect(selectedMovie?.movieId, detailsBackOwner, returnToLibraryOnDetailBack) {
        if (selectedMovie != null) {
            PlaybackInteractionBridge.registerBackAction(detailsBackOwner, ::closeMovieDetails)
        }
        onDispose {
            PlaybackInteractionBridge.clearBackAction(detailsBackOwner)
        }
    }

    LaunchedEffect(sourceId) {
        loading = true
        refreshError = null
        when (val result = featureRuntime.refresh(sourceId)) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> refreshError = result.error
        }
        loading = false
    }

    val sections = remember(catalog.categories, catalog.continueWatching, catalog.movies) {
        buildList {
            if (catalog.continueWatching.isNotEmpty()) {
                add(TvMovieSection(MOVIES_CONTINUE_KEY, "Continue Watching"))
            }
            add(TvMovieSection(MOVIES_ALL_KEY, "All Movies"))
            if (catalog.movies.any(VodMovie::isFavorite)) {
                add(TvMovieSection(MOVIES_FAVORITES_KEY, "Favorites"))
            }
            catalog.categories.forEach { category ->
                add(
                    TvMovieSection(
                        key = MOVIES_CATEGORY_PREFIX + category.providerCategoryKey,
                        label = category.name,
                    ),
                )
            }
        }
    }

    LaunchedEffect(sections, selectedSectionKey) {
        if (sections.none { it.key == selectedSectionKey }) {
            selectedSectionKey = sections.firstOrNull()?.key ?: MOVIES_ALL_KEY
        }
    }

    val visibleMovies = remember(catalog, selectedSectionKey) {
        when (selectedSectionKey) {
            MOVIES_CONTINUE_KEY -> catalog.continueWatching
            MOVIES_ALL_KEY -> catalog.movies
            MOVIES_FAVORITES_KEY -> catalog.movies.filter(VodMovie::isFavorite)
            else -> {
                val categoryKey = selectedSectionKey.removePrefix(MOVIES_CATEGORY_PREFIX)
                catalog.movies.filter { movie -> movie.categoryKey == categoryKey }
            }
        }
    }

    val sessionDetailMovieId = onDemandPresentation.itemId.takeIf {
        onDemandPresentation.kind == OnDemandContentKind.MOVIE &&
            onDemandPresentation.sourceId == sourceId &&
            !onDemandPresentation.isMoviePlayback
    }
    val requestedTargetId = requestedMovieId ?: sessionDetailMovieId

    LaunchedEffect(requestedTargetId, catalog.movies, catalog.categories) {
        val targetId = requestedTargetId ?: return@LaunchedEffect
        val target = catalog.movies.firstOrNull { movie -> movie.movieId == targetId }
            ?: return@LaunchedEffect
        if (selectedMovie?.movieId != target.movieId) {
            selectedSectionKey = target.categoryKey
                ?.let { MOVIES_CATEGORY_PREFIX + it }
                ?.takeIf { key -> sections.any { section -> section.key == key } }
                ?: MOVIES_ALL_KEY
            selectedMovie = target
        }
        if (requestedMovieId == targetId) {
            onRequestedMovieConsumed()
        }
    }

    LaunchedEffect(catalog.movies, selectedMovie?.movieId) {
        val selectedId = selectedMovie?.movieId ?: return@LaunchedEffect
        catalog.movies.firstOrNull { movie -> movie.movieId == selectedId }?.let { refreshed ->
            selectedMovie = refreshed
        }
    }

    LaunchedEffect(selectedMovie?.movieId) {
        val movie = selectedMovie
        if (movie == null) {
            details = null
            detailsError = null
            detailsLoading = false
            return@LaunchedEffect
        }
        detailsLoading = true
        detailsError = null
        details = when (val result = featureRuntime.details(sourceId, movie.movieId)) {
            is SourceResult.Success -> result.value.copy(
                movie = result.value.movie.copy(
                    isFavorite = movie.isFavorite,
                    positionMs = movie.positionMs,
                    durationMs = movie.durationMs ?: result.value.movie.durationMs,
                    progressCompleted = movie.progressCompleted,
                    progressUpdatedAtEpochMillis = movie.progressUpdatedAtEpochMillis,
                ),
            )
            is SourceResult.Failure -> {
                detailsError = result.error
                null
            }
        }
        detailsLoading = false
    }

    val openedMovie = selectedMovie
    if (openedMovie != null) {
        TvMovieDetailsScreen(
            movie = openedMovie,
            details = details,
            loading = detailsLoading,
            failed = detailsError != null,
            onPlay = { startMovie(openedMovie, fromBeginning = false) },
            onPlayFromBeginning = { startMovie(openedMovie, fromBeginning = true) },
            onFavoriteChanged = { favorite -> setMovieFavorite(openedMovie, favorite) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    TvMoviesCatalogScreen(
        sections = sections,
        selectedSectionKey = selectedSectionKey,
        movies = visibleMovies,
        totalMovieCount = catalog.movies.size,
        loading = loading,
        refreshFailed = refreshError != null,
        movieFocusTargetId = movieFocusTargetId,
        movieFocusRequestGeneration = movieFocusRequestGeneration,
        onSectionSelected = { selectedSectionKey = it },
        onMovieFocusRequested = { movieId ->
            movieFocusTargetId = movieId
            movieFocusRequestGeneration += 1
        },
        onMovieSelected = ::openMovie,
        onRetry = ::refreshCatalog,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun TvMoviesCatalogScreen(
    sections: List<TvMovieSection>,
    selectedSectionKey: String,
    movies: List<VodMovie>,
    totalMovieCount: Int,
    loading: Boolean,
    refreshFailed: Boolean,
    movieFocusTargetId: String?,
    movieFocusRequestGeneration: Int,
    onSectionSelected: (String) -> Unit,
    onMovieFocusRequested: (String) -> Unit,
    onMovieSelected: (VodMovie) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryFocusRequester = remember { FocusRequester() }
    val movieFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val selectedSectionLabel = sections.firstOrNull { it.key == selectedSectionKey }?.label ?: "Movies"

    LaunchedEffect(movieFocusRequestGeneration, movieFocusTargetId, movies) {
        if (movieFocusRequestGeneration <= 0) return@LaunchedEffect
        val targetId = movieFocusTargetId ?: return@LaunchedEffect
        val index = movies.indexOfFirst { movie -> movie.movieId == targetId }
        if (index < 0) return@LaunchedEffect
        gridState.scrollToItem(index)
        withFrameNanos { }
        withFrameNanos { }
        movieFocusRequester.requestFocus()
    }

    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Movies",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$totalMovieCount titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sections, key = TvMovieSection::key) { section ->
                        TvMovieSectionRow(
                            section = section,
                            selected = section.key == selectedSectionKey,
                            focusRequester = categoryFocusRequester.takeIf {
                                section.key == selectedSectionKey
                            },
                            onClick = { onSectionSelected(section.key) },
                            onRight = {
                                val movie = movies.firstOrNull()
                                if (movie == null) {
                                    false
                                } else {
                                    onMovieFocusRequested(movie.movieId)
                                    true
                                }
                            },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedSectionLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${movies.size} available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    refreshFailed -> TextButton(onClick = onRetry) { Text("Retry refresh") }
                }
            }

            when {
                loading && totalMovieCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                movies.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (refreshFailed && totalMovieCount == 0) {
                            "Movies could not be loaded"
                        } else {
                            "No Movies in this section"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(MOVIE_GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    gridItemsIndexed(
                        items = movies,
                        key = { _, movie -> movie.movieId },
                    ) { index, movie ->
                        TvMovieCard(
                            movie = movie,
                            restoreFocus = movie.movieId == movieFocusTargetId,
                            focusRequester = movieFocusRequester,
                            onLeftBoundary = {
                                if (index % MOVIE_GRID_COLUMNS == 0) {
                                    categoryFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                            onClick = { onMovieSelected(movie) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMovieSectionRow(
    section: TvMovieSection,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRight: () -> Boolean,
) {
    var focused by remember(section.key) { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    val background = when {
        focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRight()
                } else {
                    false
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = background,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvMovieCard(
    movie: VodMovie,
    restoreFocus: Boolean,
    focusRequester: FocusRequester,
    onLeftBoundary: () -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(movie.movieId) { mutableStateOf(false) }
    val requesterModifier = if (restoreFocus) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft &&
                    onLeftBoundary()
                ) {
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                RemotePoster(
                    url = movie.posterUrl,
                    title = movie.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                movieProgressFraction(movie)?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            Text(
                text = movie.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (movie.isFavorite) append("Favorite")
                    movie.rating?.let { rating ->
                        if (isNotEmpty()) append("  ·  ")
                        append("★ %.1f".format(rating))
                    }
                    if (isEmpty()) append("Movie")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvMovieDetailsScreen(
    movie: VodMovie,
    details: VodMovieDetails?,
    loading: Boolean,
    failed: Boolean,
    onPlay: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryFocusRequester = remember(movie.movieId) { FocusRequester() }

    LaunchedEffect(movie.movieId) {
        withFrameNanos { }
        primaryFocusRequester.requestFocus()
    }

    Row(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        RemotePoster(
            url = details?.posterUrl ?: movie.posterUrl,
            title = movie.name,
            modifier = Modifier
                .width(270.dp)
                .aspectRatio(2f / 3f),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = details?.movie?.name ?: movie.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = movieMetaLine(movie, details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                movieProgressLabel(movie)?.let { progress ->
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(
                modifier = Modifier.width(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvMovieActionRow(
                    label = if (movie.resumeAvailable) "Resume" else "Play",
                    focusRequester = primaryFocusRequester,
                    onClick = onPlay,
                )
                if (movie.resumeAvailable) {
                    TvMovieActionRow(
                        label = "Play from beginning",
                        onClick = onPlayFromBeginning,
                    )
                }
                TvMovieActionRow(
                    label = if (movie.isFavorite) "Remove favorite" else "Add favorite",
                    onClick = { onFavoriteChanged(!movie.isFavorite) },
                )
            }

            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                failed -> Text(
                    text = "Detailed metadata is unavailable. Playback remains available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            details?.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            details?.director?.takeIf(String::isNotBlank)?.let { director ->
                Text(
                    text = "Director · $director",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            details?.cast?.takeIf(String::isNotBlank)?.let { cast ->
                Text(
                    text = "Cast · $cast",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Back returns to the Movies context.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvMovieActionRow(
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun TvMoviesMessage(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun movieMetaLine(movie: VodMovie, details: VodMovieDetails?): String =
    listOfNotNull(
        details?.releaseDate,
        details?.durationLabel,
        details?.genre,
        details?.country,
        (details?.rating ?: movie.rating)?.let { rating -> "★ %.1f".format(rating) },
    ).joinToString("  ·  ").ifBlank { "Movie" }

private fun movieProgressLabel(movie: VodMovie): String? {
    if (!movie.resumeAvailable) return null
    val progress = movieProgressFraction(movie)
    return progress?.let { fraction ->
        "Resume available · ${(fraction * 100f).toInt().coerceIn(1, 99)}% watched"
    } ?: "Resume available"
}

private fun movieProgressFraction(movie: VodMovie): Float? {
    val position = movie.positionMs ?: return null
    val duration = movie.durationMs ?: return null
    if (position <= 0L || duration <= 0L || movie.progressCompleted) return null
    return (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
}
