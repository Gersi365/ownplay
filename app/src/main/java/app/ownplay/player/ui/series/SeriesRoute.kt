package app.ownplay.player.ui.series

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
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
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesDetails
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSeason
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.playbackStatusLabel
import app.ownplay.player.ui.vod.RemotePoster
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SERIES_EXIT_PROGRESS_SAVE_TIMEOUT_MILLIS = 1_000L

@Composable
internal fun SeriesRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    requestedSeriesId: String? = null,
    onRequestedSeriesConsumed: () -> Unit = {},
    returnToLibraryOnDetailBack: Boolean = false,
    onReturnToLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val featureRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }
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
        SeriesUnavailableState(
            title = "No playlist configured",
            body = "Add an Xtream playlist from Settings to load Series.",
            onOpenSettings = onOpenSettings,
        )
        return
    }
    if (sourceKind != SourceKinds.XTREAM) {
        SeriesUnavailableState(
            title = "Series are not available for this source",
            body = "Series and episodes currently use Xtream-compatible sources.",
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val catalog by featureRuntime.observeCatalog(sourceId).collectAsState(initial = SeriesCatalog())
    val downloads by downloadRuntime.observeAll().collectAsState(initial = emptyList())
    var loading by remember(sourceId) { mutableStateOf(false) }
    var refreshError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var query by remember(sourceId) { mutableStateOf("") }
    var categoryKey by remember(sourceId) { mutableStateOf<String?>(null) }
    var favoritesOnly by remember(sourceId) { mutableStateOf(false) }
    var selectedSeries by remember(sourceId) { mutableStateOf<SeriesSummary?>(null) }
    var details by remember(sourceId) { mutableStateOf<SeriesDetails?>(null) }
    var detailsLoading by remember(sourceId) { mutableStateOf(false) }
    var detailsError by remember(sourceId) { mutableStateOf<SourceError?>(null) }
    var selectedSeasonNumber by remember(sourceId) { mutableStateOf<Int?>(null) }
    var selectedEpisodeId by remember(sourceId) { mutableStateOf<String?>(null) }
    var playingEpisode by remember(sourceId) { mutableStateOf<SeriesEpisode?>(null) }
    var playbackReturnsToCatalog by remember(sourceId) { mutableStateOf(false) }
    var restoreCatalogFocusAfterPlayback by remember(sourceId) { mutableStateOf(false) }
    val detailsBackOwner = remember(sourceId) { Any() }

    fun closeSeriesLevel() {
        when {
            selectedEpisodeId != null -> selectedEpisodeId = null
            selectedSeasonNumber != null -> {
                selectedSeasonNumber = null
                selectedEpisodeId = null
            }
            returnToLibraryOnDetailBack -> onReturnToLibrary()
            else -> selectedSeries = null
        }
    }

    DisposableEffect(
        selectedSeries?.seriesId,
        selectedSeasonNumber,
        selectedEpisodeId,
        playingEpisode?.episodeId,
        detailsBackOwner,
        returnToLibraryOnDetailBack,
    ) {
        if (selectedSeries != null && playingEpisode == null) {
            PlaybackInteractionBridge.registerBackAction(detailsBackOwner, ::closeSeriesLevel)
        }
        onDispose {
            PlaybackInteractionBridge.clearBackAction(detailsBackOwner)
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

    fun playEpisode(episode: SeriesEpisode, returnFocusToCatalog: Boolean) {
        restoreCatalogFocusAfterPlayback = false
        playbackReturnsToCatalog = returnFocusToCatalog
        runtime.playbackController.start(
            PlaybackRequest(
                sourceId = sourceId,
                channelId = episode.episodeId,
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
                providerStreamId = episode.providerEpisodeId,
                containerExtension = episode.containerExtension,
            ),
        )
        playingEpisode = episode
    }

    fun downloadEpisode(episode: SeriesEpisode) {
        scope.launch {
            downloadRuntime.enqueue(
                OfflineDownloadSpec(
                    sourceId = sourceId,
                    mediaKind = DownloadMediaKinds.SERIES_EPISODE,
                    contentId = episode.episodeId,
                    providerStreamId = episode.providerEpisodeId,
                    title = episode.title,
                    seriesTitle = episode.seriesTitle,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    posterUrl = episode.posterUrl,
                    containerExtension = episode.containerExtension,
                ),
            )
        }
    }

    fun pauseDownload(download: OfflineDownload) {
        scope.launch { downloadRuntime.pause(download.downloadId) }
    }

    fun resumeDownload(download: OfflineDownload) {
        scope.launch { downloadRuntime.resume(download.downloadId) }
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

    LaunchedEffect(sourceId, requestedSeriesId, catalog.series) {
        val targetSeriesId = requestedSeriesId ?: return@LaunchedEffect
        val target = catalog.series.firstOrNull { item -> item.seriesId == targetSeriesId }
            ?: return@LaunchedEffect
        query = ""
        categoryKey = null
        favoritesOnly = false
        selectedSeasonNumber = null
        selectedEpisodeId = null
        restoreCatalogFocusAfterPlayback = false
        selectedSeries = target
        onRequestedSeriesConsumed()
    }

    LaunchedEffect(selectedSeries?.seriesId) {
        val selected = selectedSeries
        selectedSeasonNumber = null
        selectedEpisodeId = null
        if (selected == null) {
            details = null
            detailsError = null
            return@LaunchedEffect
        }
        detailsLoading = true
        detailsError = null
        details = when (val result = featureRuntime.details(sourceId, selected.seriesId)) {
            is SourceResult.Success -> result.value
            is SourceResult.Failure -> {
                detailsError = result.error
                null
            }
        }
        detailsLoading = false
    }

    LaunchedEffect(details, selectedSeasonNumber, selectedEpisodeId) {
        val seasonNumber = selectedSeasonNumber
        if (seasonNumber != null) {
            val season = details?.seasons?.firstOrNull { it.seasonNumber == seasonNumber }
            if (season == null) {
                selectedSeasonNumber = null
                selectedEpisodeId = null
            } else {
                val episodeId = selectedEpisodeId
                if (episodeId != null && season.episodes.none { it.episodeId == episodeId }) {
                    selectedEpisodeId = null
                }
            }
        } else if (selectedEpisodeId != null) {
            selectedEpisodeId = null
        }
    }

    val currentEpisode = playingEpisode
    if (currentEpisode != null) {
        SeriesPlaybackScreen(
            runtime = runtime,
            featureRuntime = featureRuntime,
            sourceId = sourceId,
            episode = currentEpisode,
            onExit = {
                playingEpisode = null
                if (playbackReturnsToCatalog) {
                    restoreCatalogFocusAfterPlayback = true
                }
                playbackReturnsToCatalog = false
            },
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
        return
    }

    val normalizedQuery = query.trim().lowercase()
    val visibleSeries = catalog.series.filter { item ->
        (categoryKey == null || item.categoryKey == categoryKey) &&
            (!favoritesOnly || item.isFavorite) &&
            (normalizedQuery.isBlank() || item.name.lowercase().contains(normalizedQuery))
    }

    val portraitSelection = selectedSeries
    if (!isLandscape && portraitSelection != null) {
        SeriesDetailsPane(
            selected = portraitSelection,
            details = details,
            loading = detailsLoading,
            error = detailsError,
            selectedSeasonNumber = selectedSeasonNumber,
            selectedEpisodeId = selectedEpisodeId,
            downloads = downloads,
            focusBackOnEntry = true,
            onSeasonSelected = {
                selectedSeasonNumber = it
                selectedEpisodeId = null
            },
            onEpisodeSelected = { selectedEpisodeId = it },
            onFavoriteChanged = { favorite ->
                selectedSeries = portraitSelection.copy(isFavorite = favorite)
                scope.launch {
                    featureRuntime.setFavorite(sourceId, portraitSelection.seriesId, favorite)
                }
            },
            onPlay = { episode -> playEpisode(episode, returnFocusToCatalog = false) },
            onDownload = ::downloadEpisode,
            onPauseDownload = ::pauseDownload,
            onResumeDownload = ::resumeDownload,
            onClearProgress = { episode ->
                scope.launch {
                    featureRuntime.clearEpisodeProgress(sourceId, episode.episodeId)
                }
            },
            onClose = ::closeSeriesLevel,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeriesCatalogPane(
            catalog = catalog,
            series = visibleSeries,
            loading = loading,
            refreshError = refreshError,
            query = query,
            selectedCategoryKey = categoryKey,
            favoritesOnly = favoritesOnly,
            selectedSeriesId = selectedSeries?.seriesId,
            restoreFocusOnEntry = restoreCatalogFocusAfterPlayback,
            onFocusRestored = { restoreCatalogFocusAfterPlayback = false },
            onQueryChanged = { query = it },
            onCategoryChanged = { categoryKey = it },
            onFavoritesChanged = { favoritesOnly = it },
            onRefresh = ::refresh,
            onSeriesSelected = {
                restoreCatalogFocusAfterPlayback = false
                selectedSeasonNumber = null
                selectedEpisodeId = null
                selectedSeries = it
            },
            onContinueEpisode = { episode -> playEpisode(episode, returnFocusToCatalog = true) },
            modifier = Modifier.weight(if (selectedSeries == null) 1f else 0.58f),
        )
        selectedSeries?.let { selected ->
            SeriesDetailsPane(
                selected = selected,
                details = details,
                loading = detailsLoading,
                error = detailsError,
                selectedSeasonNumber = selectedSeasonNumber,
                selectedEpisodeId = selectedEpisodeId,
                downloads = downloads,
                focusBackOnEntry = returnToLibraryOnDetailBack,
                onSeasonSelected = {
                    selectedSeasonNumber = it
                    selectedEpisodeId = null
                },
                onEpisodeSelected = { selectedEpisodeId = it },
                onFavoriteChanged = { favorite ->
                    selectedSeries = selected.copy(isFavorite = favorite)
                    scope.launch {
                        featureRuntime.setFavorite(sourceId, selected.seriesId, favorite)
                    }
                },
                onPlay = { episode -> playEpisode(episode, returnFocusToCatalog = false) },
                onDownload = ::downloadEpisode,
                onPauseDownload = ::pauseDownload,
                onResumeDownload = ::resumeDownload,
                onClearProgress = { episode ->
                    scope.launch {
                        featureRuntime.clearEpisodeProgress(sourceId, episode.episodeId)
                    }
                },
                onClose = ::closeSeriesLevel,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SeriesCatalogPane(
    catalog: SeriesCatalog,
    series: List<SeriesSummary>,
    loading: Boolean,
    refreshError: SourceError?,
    query: String,
    selectedCategoryKey: String?,
    favoritesOnly: Boolean,
    selectedSeriesId: String?,
    restoreFocusOnEntry: Boolean,
    onFocusRestored: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onFavoritesChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSeriesSelected: (SeriesSummary) -> Unit,
    onContinueEpisode: (SeriesEpisode) -> Unit,
    modifier: Modifier,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val catalogReturnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTelevision, restoreFocusOnEntry) {
        if (isTelevision && restoreFocusOnEntry) {
            catalogReturnFocusRequester.requestFocus()
        }
        if (restoreFocusOnEntry) {
            onFocusRestored()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Series", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${series.size} of ${catalog.series.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Refresh")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search series") },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedCategoryKey == null,
                onClick = { onCategoryChanged(null) },
                label = { Text("All") },
                modifier = Modifier.focusRequester(catalogReturnFocusRequester),
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavoritesChanged(!favoritesOnly) },
                label = { Text("Favorites") },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(catalog.categories, key = { it.categoryId }) { category ->
                FilterChip(
                    selected = selectedCategoryKey == category.providerCategoryKey,
                    onClick = { onCategoryChanged(category.providerCategoryKey) },
                    label = { Text(category.name, maxLines = 1) },
                )
            }
        }
        refreshError?.let {
            Text(
                text = "Series refresh failed. Existing catalog remains available.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        if (catalog.continueWatching.isNotEmpty()) {
            Text(
                "Continue Watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalog.continueWatching, key = { it.episodeId }) { episode ->
                    Surface(
                        modifier = Modifier
                            .width(210.dp)
                            .clickable { onContinueEpisode(episode) },
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                episode.seriesTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "S${episode.seasonNumber} · E${episode.episodeNumber} · ${episode.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(series, key = { it.seriesId }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeriesSelected(item) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSeriesId == item.seriesId) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemotePoster(
                            url = item.posterUrl,
                            title = item.name,
                            modifier = Modifier
                                .width(64.dp)
                                .aspectRatio(2f / 3f),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            item.rating?.let { rating ->
                                Text(
                                    "Rating ${"%.1f".format(rating)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.description?.takeIf(String::isNotBlank)?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.isFavorite) {
                                Text("Favorite", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeriesPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    featureRuntime: SeriesFeatureRuntime,
    sourceId: String,
    episode: SeriesEpisode,
    onExit: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackControls = PlaybackPresentationPolicy.controlsFor(playbackState)
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val scope = rememberCoroutineScope()
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var exitRequested by remember(episode.episodeId) { mutableStateOf(false) }
    val backOwner = remember(episode.episodeId) { Any() }
    val backFocusRequester = remember(episode.episodeId) { FocusRequester() }

    fun exitPlayback() {
        if (exitRequested) return
        exitRequested = true
        val view = playerView
        scope.launch {
            val player = view?.player
            if (player != null) {
                withTimeoutOrNull(SERIES_EXIT_PROGRESS_SAVE_TIMEOUT_MILLIS) {
                    featureRuntime.saveEpisodeProgress(
                        sourceId = sourceId,
                        episodeId = episode.episodeId,
                        positionMs = player.currentPosition,
                        durationMs = player.duration.takeIf {
                            it != C.TIME_UNSET && it > 0L
                        },
                    )
                }
            }
            onExit()
        }
    }

    DisposableEffect(backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, ::exitPlayback)
        onDispose {
            runtime.playbackController.stopIfCurrent(
                sourceId = sourceId,
                channelId = episode.episodeId,
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
            )
            PlaybackInteractionBridge.clearBackAction(backOwner)
            onFullscreenStateChanged(false)
        }
    }

    LaunchedEffect(isTelevision, playbackState, playerView, episode.episodeId) {
        if (!isTelevision) return@LaunchedEffect
        if (playbackState is PlaybackState.Failed) {
            backFocusRequester.requestFocus()
            return@LaunchedEffect
        }
        val view = playerView ?: return@LaunchedEffect
        view.isFocusable = true
        view.showController()
        view.requestFocus()
    }

    LaunchedEffect(playerView, episode.episodeId) {
        val view = playerView ?: return@LaunchedEffect
        delay(300)
        val player = view.player
        val resumePosition = episode.positionMs ?: 0L
        if (resumePosition > 0L && player != null && player.currentPosition < 1_000L) {
            player.seekTo(resumePosition)
        }
        while (currentCoroutineContext().isActive) {
            delay(2_000L)
            val activePlayer = view.player ?: continue
            val duration = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            featureRuntime.saveEpisodeProgress(
                sourceId = sourceId,
                episodeId = episode.episodeId,
                positionMs = activePlayer.currentPosition,
                durationMs = duration,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(episode.seriesTitle, fontWeight = FontWeight.Bold)
                Text(
                    "S${episode.seasonNumber} · E${episode.episodeNumber} · ${episode.title}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                modifier = Modifier.focusRequester(backFocusRequester),
                enabled = !exitRequested,
                onClick = ::exitPlayback,
            ) { Text("Back") }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).also { view ->
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlaybackInteractionBridge.bind(
                            output = runtime.playbackVideoOutput,
                            view = view,
                            showNativeController = true,
                        )
                        playerView = view
                    }
                },
                update = { view ->
                    PlaybackInteractionBridge.bind(
                        output = runtime.playbackVideoOutput,
                        view = view,
                        showNativeController = true,
                    )
                    playerView = view
                },
                onRelease = { view ->
                    PlaybackInteractionBridge.unbind(runtime.playbackVideoOutput, view)
                    if (playerView === view) playerView = null
                },
            )
            if (playbackState is PlaybackState.Loading) {
                CircularProgressIndicator()
            }
            val failedState = playbackState as? PlaybackState.Failed
            if (failedState != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(playbackStatusLabel(failedState))
                        if (playbackControls.canRetry) {
                            TextButton(onClick = runtime.playbackController::retry) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesUnavailableState(
    title: String,
    body: String,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }
    }
}

private fun downloadProgressLabel(download: OfflineDownload): String {
    val downloaded = humanBytes(download.bytesDownloaded)
    val totalBytes = download.totalBytes?.takeIf { it > 0L }
    val total = totalBytes?.let(::humanBytes)
    val prefix = when (download.state) {
        DownloadStates.PAUSED -> "Paused · "
        DownloadStates.QUEUED -> "Queued · "
        else -> ""
    }
    if (totalBytes == null || total == null) return "$prefix$downloaded"
    val percent = ((download.bytesDownloaded.toDouble() / totalBytes.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
    return "$prefix$downloaded / $total · $percent%"
}

private fun humanBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1_073_741_824L -> "%.1f GB".format(safe / 1_073_741_824.0)
        safe >= 1_048_576L -> "%.1f MB".format(safe / 1_048_576.0)
        safe >= 1_024L -> "%.1f KB".format(safe / 1_024.0)
        else -> "$safe B"
    }
}