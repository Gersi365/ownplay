package app.ownplay.player.ui.vod

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.download.OfflineDownloadSpec
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import app.ownplay.player.vod.VodMovieDetails
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

enum class VodSortOrder {
    PROVIDER,
    A_TO_Z,
    NEWEST,
    RATING,
}

@Composable
internal fun VodRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    onOpenLive: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val featureRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val downloadRuntime = remember(context) {
        OfflineDownloadFeatureRuntime(context.applicationContext)
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(featureRuntime) {
        onDispose { featureRuntime.close() }
    }
    DisposableEffect(downloadRuntime) {
        onDispose { downloadRuntime.close() }
    }

    if (sourceId == null) {
        VodUnavailableState(
            title = "No playlist configured",
            body = "Add an Xtream playlist from Settings to load Movies.",
            onOpenSettings = onOpenSettings,
        )
        return
    }
    if (sourceKind != SourceKinds.XTREAM) {
        VodUnavailableState(
            title = "Movies are not available for this source yet",
            body = "Phase 011 currently loads VOD from Xtream sources. Your Live source remains unchanged.",
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val catalog by featureRuntime.observeCatalog(sourceId).collectAsState(initial = VodCatalog())
    val downloads by downloadRuntime.observeAll().collectAsState(initial = emptyList())
    var loading by remember(sourceId) { mutableStateOf(false) }
    var refreshError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var query by remember(sourceId) { mutableStateOf("") }
    var selectedCategoryKey by remember(sourceId) { mutableStateOf<String?>(null) }
    var favoritesOnly by remember(sourceId) { mutableStateOf(false) }
    var sortOrder by remember(sourceId) { mutableStateOf(VodSortOrder.PROVIDER) }
    var selectedMovie by remember(sourceId) { mutableStateOf<VodMovie?>(null) }
    var details by remember(sourceId) { mutableStateOf<VodMovieDetails?>(null) }
    var detailsLoading by remember(sourceId) { mutableStateOf(false) }
    var detailsError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var playingMovie by remember(sourceId) { mutableStateOf<VodMovie?>(null) }

    fun downloadFor(movie: VodMovie): OfflineDownload? = downloads.firstOrNull { download ->
        download.sourceId == sourceId &&
            download.mediaKind == DownloadMediaKinds.MOVIE &&
            download.contentId == movie.movieId
    }

    fun enqueueMovieDownload(movie: VodMovie) {
        scope.launch {
            downloadRuntime.enqueue(
                OfflineDownloadSpec(
                    sourceId = sourceId,
                    mediaKind = DownloadMediaKinds.MOVIE,
                    contentId = movie.movieId,
                    providerStreamId = movie.providerStreamId,
                    title = movie.name,
                    posterUrl = movie.posterUrl,
                    containerExtension = movie.containerExtension,
                ),
            )
        }
    }

    fun refresh() {
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

    LaunchedEffect(sourceId) {
        loading = true
        refreshError = null
        when (val result = featureRuntime.refresh(sourceId)) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> refreshError = result.error
        }
        loading = false
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

    val filteredMovies = remember(
        catalog.movies,
        query,
        selectedCategoryKey,
        favoritesOnly,
        sortOrder,
    ) {
        val normalizedQuery = query.trim().lowercase()
        catalog.movies
            .asSequence()
            .filter { movie -> selectedCategoryKey == null || movie.categoryKey == selectedCategoryKey }
            .filter { movie -> !favoritesOnly || movie.isFavorite }
            .filter { movie -> normalizedQuery.isBlank() || movie.name.lowercase().contains(normalizedQuery) }
            .let { sequence ->
                when (sortOrder) {
                    VodSortOrder.PROVIDER -> sequence.sortedBy { it.providerStreamId }
                    VodSortOrder.A_TO_Z -> sequence.sortedBy { it.name.lowercase() }
                    VodSortOrder.NEWEST -> sequence.sortedByDescending { it.addedAtEpochSeconds ?: Long.MIN_VALUE }
                    VodSortOrder.RATING -> sequence.sortedByDescending { it.rating ?: Double.NEGATIVE_INFINITY }
                }
            }
            .toList()
    }

    val movieToPlay = playingMovie
    if (movieToPlay != null) {
        VodPlaybackScreen(
            runtime = runtime,
            featureRuntime = featureRuntime,
            sourceId = sourceId,
            movie = movieToPlay,
            onExit = {
                runtime.playbackController.stop()
                playingMovie = null
            },
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
        return
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MovieNavigationRail(
                catalog = catalog,
                selectedCategoryKey = selectedCategoryKey,
                onCategorySelected = { selectedCategoryKey = it },
                onOpenLive = onOpenLive,
                onOpenSeries = onOpenSeries,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .widthIn(min = 170.dp, max = 220.dp)
                    .fillMaxHeight(),
            )
            MoviesCatalogContent(
                catalog = catalog,
                movies = filteredMovies,
                loading = loading,
                refreshError = refreshError,
                query = query,
                favoritesOnly = favoritesOnly,
                sortOrder = sortOrder,
                onQueryChanged = { query = it },
                onFavoritesChanged = { favoritesOnly = it },
                onSortChanged = { sortOrder = it },
                onRefresh = ::refresh,
                onMovieSelected = { selectedMovie = it },
                showCategoryStrip = false,
                selectedCategoryKey = selectedCategoryKey,
                onCategorySelected = { selectedCategoryKey = it },
                modifier = Modifier.weight(if (selectedMovie == null) 1f else 0.63f),
            )
            selectedMovie?.let { movie ->
                MovieDetailsPane(
                    movie = movie,
                    details = details,
                    loading = detailsLoading,
                    error = detailsError,
                    download = downloadFor(movie),
                    onDismiss = { selectedMovie = null },
                    onFavoriteChanged = { favorite ->
                        scope.launch {
                            featureRuntime.setFavorite(sourceId, movie.movieId, favorite)
                        }
                    },
                    onDownload = ::enqueueMovieDownload,
                    onPlay = { target ->
                        runtime.playbackController.start(
                            PlaybackRequest(
                                sourceId = sourceId,
                                channelId = target.movieId,
                                mediaKind = PlaybackMediaKind.MOVIE,
                            ),
                        )
                        playingMovie = target
                    },
                    modifier = Modifier
                        .weight(0.37f)
                        .fillMaxHeight(),
                )
            }
        }
    } else {
        MoviesCatalogContent(
            catalog = catalog,
            movies = filteredMovies,
            loading = loading,
            refreshError = refreshError,
            query = query,
            favoritesOnly = favoritesOnly,
            sortOrder = sortOrder,
            onQueryChanged = { query = it },
            onFavoritesChanged = { favoritesOnly = it },
            onSortChanged = { sortOrder = it },
            onRefresh = ::refresh,
            onMovieSelected = { selectedMovie = it },
            showCategoryStrip = true,
            selectedCategoryKey = selectedCategoryKey,
            onCategorySelected = { selectedCategoryKey = it },
            modifier = Modifier.fillMaxSize(),
        )

        selectedMovie?.let { movie ->
            Dialog(onDismissRequest = { selectedMovie = null }) {
                MovieDetailsPane(
                    movie = movie,
                    details = details,
                    loading = detailsLoading,
                    error = detailsError,
                    download = downloadFor(movie),
                    onDismiss = { selectedMovie = null },
                    onFavoriteChanged = { favorite ->
                        scope.launch { featureRuntime.setFavorite(sourceId, movie.movieId, favorite) }
                    },
                    onDownload = ::enqueueMovieDownload,
                    onPlay = { target ->
                        runtime.playbackController.start(
                            PlaybackRequest(
                                sourceId = sourceId,
                                channelId = target.movieId,
                                mediaKind = PlaybackMediaKind.MOVIE,
                            ),
                        )
                        selectedMovie = null
                        playingMovie = target
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.90f),
                )
            }
        }
    }
}

@Composable
private fun MoviesCatalogContent(
    catalog: VodCatalog,
    movies: List<VodMovie>,
    loading: Boolean,
    refreshError: SourceError?,
    query: String,
    favoritesOnly: Boolean,
    sortOrder: VodSortOrder,
    onQueryChanged: (String) -> Unit,
    onFavoritesChanged: (Boolean) -> Unit,
    onSortChanged: (VodSortOrder) -> Unit,
    onRefresh: () -> Unit,
    onMovieSelected: (VodMovie) -> Unit,
    showCategoryStrip: Boolean,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Movies",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${movies.size} of ${catalog.movies.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavoritesChanged(!favoritesOnly) },
                label = { Text("Favorites") },
                leadingIcon = {
                    Icon(
                        if (favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Movies")
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search movies") },
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VodSortOrder.entries.forEach { order ->
                FilterChip(
                    selected = sortOrder == order,
                    onClick = { onSortChanged(order) },
                    label = {
                        Text(
                            when (order) {
                                VodSortOrder.PROVIDER -> "Provider"
                                VodSortOrder.A_TO_Z -> "A–Z"
                                VodSortOrder.NEWEST -> "Newest"
                                VodSortOrder.RATING -> "Rating"
                            },
                        )
                    },
                )
            }
        }

        if (showCategoryStrip) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "vod-all") {
                    FilterChip(
                        selected = selectedCategoryKey == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text("All") },
                    )
                }
                items(catalog.categories, key = { it.categoryId }) { category ->
                    FilterChip(
                        selected = selectedCategoryKey == category.providerCategoryKey,
                        onClick = { onCategorySelected(category.providerCategoryKey) },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        if (refreshError != null && catalog.movies.isNotEmpty()) {
            Text(
                text = "Refresh failed. Showing saved Movies.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (catalog.continueWatching.isNotEmpty() && query.isBlank() && !favoritesOnly) {
            Text(
                text = "Continue Watching",
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(catalog.continueWatching, key = { "continue-${it.movieId}" }) { movie ->
                    ContinueWatchingCard(movie = movie, onClick = { onMovieSelected(movie) })
                }
            }
        }

        HorizontalDivider()

        when {
            loading && catalog.movies.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            refreshError != null && catalog.movies.isEmpty() -> VodEmptyCatalog(
                title = "Movies could not be loaded",
                body = sourceErrorLabel(refreshError),
                actionLabel = "Retry",
                onAction = onRefresh,
            )
            movies.isEmpty() -> VodEmptyCatalog(
                title = "No matching Movies",
                body = "Try another category, search term or filter.",
                actionLabel = null,
                onAction = {},
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(movies, key = { it.movieId }) { movie ->
                    MovieCard(movie = movie, onClick = { onMovieSelected(movie) })
                }
            }
        }
    }
}

@Composable
private fun MovieNavigationRail(
    catalog: VodCatalog,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    onOpenLive: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NavRailItem(Icons.Filled.LiveTv, "Live", false, onOpenLive)
            NavRailItem(Icons.Filled.Movie, "Movies", true, {})
            NavRailItem(Icons.Filled.VideoLibrary, "Series", false, onOpenSeries)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Movie categories",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "movie-all") {
                    MovieCategoryRow(
                        title = "All Movies",
                        selected = selectedCategoryKey == null,
                        onClick = { onCategorySelected(null) },
                    )
                }
                items(catalog.categories, key = { it.categoryId }) { category ->
                    MovieCategoryRow(
                        title = category.name,
                        selected = selectedCategoryKey == category.providerCategoryKey,
                        onClick = { onCategorySelected(category.providerCategoryKey) },
                    )
                }
            }
            HorizontalDivider()
            NavRailItem(Icons.Filled.Settings, "Settings", false, onOpenSettings)
        }
    }
}

@Composable
private fun NavRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MovieCategoryRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .padding(horizontal = 9.dp, vertical = 7.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun MovieCard(
    movie: VodMovie,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box {
            RemotePoster(
                url = movie.posterUrl,
                title = movie.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            if (movie.isFavorite) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            movieProgressFraction(movie)?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = movie.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        movie.rating?.let { rating ->
            Text(
                text = "★ %.1f".format(rating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    movie: VodMovie,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RemotePoster(
                url = movie.posterUrl,
                title = movie.name,
                modifier = Modifier
                    .width(54.dp)
                    .aspectRatio(2f / 3f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsPane(
    movie: VodMovie,
    details: VodMovieDetails?,
    loading: Boolean,
    error: SourceError?,
    download: OfflineDownload?,
    onDismiss: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onDownload: (VodMovie) -> Unit,
    onPlay: (VodMovie) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = details?.movie?.name ?: movie.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Close details")
                }
            }
            RemotePoster(
                url = details?.posterUrl ?: movie.posterUrl,
                title = movie.name,
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(2f / 3f)
                    .align(Alignment.CenterHorizontally),
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                )
            }
            if (error != null) {
                Text(
                    text = "Detailed metadata unavailable. Catalog playback remains available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            details?.let { info ->
                val meta = listOfNotNull(
                    info.releaseDate,
                    info.durationLabel,
                    info.genre,
                    info.country,
                    info.rating?.let { "★ %.1f".format(it) },
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
                info.director?.takeIf(String::isNotBlank)?.let { director ->
                    Text("Director: $director", style = MaterialTheme.typography.bodySmall)
                }
                info.cast?.takeIf(String::isNotBlank)?.let { cast ->
                    Text("Cast: $cast", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onPlay(movie) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (movie.resumeAvailable) "Resume" else "Play")
                }
                FilledTonalButton(
                    onClick = { onFavoriteChanged(!movie.isFavorite) },
                ) {
                    Icon(
                        if (movie.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                    )
                }
            }

            val target = details?.movie ?: movie
            val downloadEnabled = download == null || download.state == DownloadStates.FAILED
            val downloadLabel = when (download?.state) {
                DownloadStates.QUEUED -> "Queued"
                DownloadStates.DOWNLOADING -> "Downloading"
                DownloadStates.COMPLETED -> "Downloaded"
                DownloadStates.FAILED -> "Retry download"
                else -> "Download"
            }
            FilledTonalButton(
                onClick = { onDownload(target) },
                enabled = downloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (download?.state == DownloadStates.COMPLETED) {
                        Icons.Filled.DownloadDone
                    } else {
                        Icons.Filled.Download
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(downloadLabel)
            }
            if (download?.state == DownloadStates.DOWNLOADING) {
                val progress = download.progressFraction
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (download?.state == DownloadStates.FAILED) {
                Text(
                    text = download.failureReason ?: "Download failed. Retry when the source is available.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VodPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    featureRuntime: VodFeatureRuntime,
    sourceId: String,
    movie: VodMovie,
    onExit: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    var playerView by remember(movie.movieId) { mutableStateOf<PlayerView?>(null) }
    var currentPosition by remember(movie.movieId) { mutableStateOf(movie.positionMs ?: 0L) }
    var duration by remember(movie.movieId) { mutableStateOf(movie.durationMs ?: 0L) }
    var resumeApplied by remember(movie.movieId) { mutableStateOf(false) }

    DisposableEffect(movie.movieId) {
        onFullscreenStateChanged(true)
        onDispose {
            val lastPosition = currentPosition
            val lastDuration = duration.takeIf { it > 0L }
            scope.launch {
                featureRuntime.saveProgress(sourceId, movie.movieId, lastPosition, lastDuration)
            }
            onFullscreenStateChanged(false)
        }
    }

    LaunchedEffect(playbackState, playerView, movie.movieId) {
        val stateRequest = when (val state = playbackState) {
            is PlaybackState.Playing -> state.request
            is PlaybackState.Paused -> state.request
            else -> null
        }
        if (
            !resumeApplied &&
            stateRequest?.mediaKind == PlaybackMediaKind.MOVIE &&
            stateRequest.channelId == movie.movieId
        ) {
            movie.positionMs?.takeIf { it > 5_000L && !movie.progressCompleted }?.let { position ->
                playerView?.player?.seekTo(position)
                currentPosition = position
            }
            resumeApplied = true
        }
    }

    LaunchedEffect(playerView, movie.movieId) {
        var saveTick = 0
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            val player = playerView?.player ?: continue
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: duration
            saveTick += 1
            if (saveTick >= 5) {
                saveTick = 0
                featureRuntime.saveProgress(
                    sourceId = sourceId,
                    movieId = movie.movieId,
                    positionMs = currentPosition,
                    durationMs = duration.takeIf { it > 0L },
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        runtime.playbackVideoOutput.bind(this)
                        playerView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    playerView = view
                },
                onRelease = { view ->
                    runtime.playbackVideoOutput.unbind(view)
                    if (playerView === view) playerView = null
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = movie.name,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val maxDuration = max(duration, 1L)
                Slider(
                    value = currentPosition.coerceIn(0L, maxDuration).toFloat(),
                    onValueChange = { currentPosition = it.toLong() },
                    onValueChangeFinished = {
                        playerView?.player?.seekTo(currentPosition)
                    },
                    valueRange = 0f..maxDuration.toFloat(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            when (playbackState) {
                                is PlaybackState.Playing -> runtime.playbackController.pause()
                                is PlaybackState.Paused -> runtime.playbackController.play()
                                is PlaybackState.Failed -> runtime.playbackController.retry()
                                else -> Unit
                            }
                        },
                    ) {
                        Icon(
                            if (playbackState is PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    when (playbackState) {
                        is PlaybackState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        is PlaybackState.Failed -> Text(
                            text = "Playback failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun VodUnavailableState(
    title: String,
    body: String,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }
    }
}

@Composable
private fun VodEmptyCatalog(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actionLabel?.let { label -> TextButton(onClick = onAction) { Text(label) } }
        }
    }
}

private fun movieProgressFraction(movie: VodMovie): Float? {
    val position = movie.positionMs ?: return null
    val duration = movie.durationMs ?: return null
    if (position <= 0L || duration <= 0L || movie.progressCompleted) return null
    return (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

private fun sourceErrorLabel(error: SourceError): String = when (error) {
    SourceError.AuthenticationFailed -> "Provider authentication failed."
    SourceError.CredentialUnavailable -> "Saved credentials are unavailable."
    SourceError.NetworkUnavailable -> "Network unavailable."
    SourceError.Timeout -> "Provider request timed out."
    SourceError.CleartextTransportRequiresOptIn -> "This source requires cleartext opt-in in Settings."
    SourceError.MalformedResponse -> "Provider returned malformed VOD data."
    is SourceError.HttpFailure -> "Provider returned HTTP ${error.statusCode}."
    else -> "Movies could not be refreshed."
}

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
