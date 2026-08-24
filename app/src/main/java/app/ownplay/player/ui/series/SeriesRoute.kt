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
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesDetails
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.vod.RemotePoster
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun SeriesRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
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
    var playingEpisode by remember(sourceId) { mutableStateOf<SeriesEpisode?>(null) }

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

    fun playEpisode(episode: SeriesEpisode) {
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

    fun downloadFor(episode: SeriesEpisode): OfflineDownload? = downloads.firstOrNull { download ->
        download.sourceId == sourceId &&
            download.mediaKind == DownloadMediaKinds.SERIES_EPISODE &&
            download.contentId == episode.episodeId
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

    LaunchedEffect(sourceId) {
        loading = true
        refreshError = null
        when (val result = featureRuntime.refresh(sourceId)) {
            is SourceResult.Success -> Unit
            is SourceResult.Failure -> refreshError = result.error
        }
        loading = false
    }

    LaunchedEffect(selectedSeries?.seriesId) {
        val selected = selectedSeries
        if (selected == null) {
            details = null
            detailsError = null
            selectedSeasonNumber = null
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
        selectedSeasonNumber = details?.seasons?.firstOrNull()?.seasonNumber
        detailsLoading = false
    }

    val currentEpisode = playingEpisode
    if (currentEpisode != null) {
        SeriesPlaybackScreen(
            runtime = runtime,
            featureRuntime = featureRuntime,
            sourceId = sourceId,
            episode = currentEpisode,
            onExit = {
                runtime.playbackController.stop()
                playingEpisode = null
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
            downloads = downloads,
            onSeasonSelected = { selectedSeasonNumber = it },
            onFavoriteChanged = { favorite ->
                selectedSeries = portraitSelection.copy(isFavorite = favorite)
                scope.launch {
                    featureRuntime.setFavorite(sourceId, portraitSelection.seriesId, favorite)
                }
            },
            onPlay = ::playEpisode,
            onDownload = ::downloadEpisode,
            onClearProgress = { episode ->
                scope.launch {
                    featureRuntime.clearEpisodeProgress(sourceId, episode.episodeId)
                }
            },
            onClose = { selectedSeries = null },
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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
            onQueryChanged = { query = it },
            onCategoryChanged = { categoryKey = it },
            onFavoritesChanged = { favoritesOnly = it },
            onRefresh = ::refresh,
            onSeriesSelected = { selectedSeries = it },
            onContinueEpisode = ::playEpisode,
            modifier = Modifier.weight(if (selectedSeries == null) 1f else 0.58f),
        )
        selectedSeries?.let { selected ->
            SeriesDetailsPane(
                selected = selected,
                details = details,
                loading = detailsLoading,
                error = detailsError,
                selectedSeasonNumber = selectedSeasonNumber,
                downloads = downloads,
                onSeasonSelected = { selectedSeasonNumber = it },
                onFavoriteChanged = { favorite ->
                    selectedSeries = selected.copy(isFavorite = favorite)
                    scope.launch {
                        featureRuntime.setFavorite(sourceId, selected.seriesId, favorite)
                    }
                },
                onPlay = ::playEpisode,
                onDownload = ::downloadEpisode,
                onClearProgress = { episode ->
                    scope.launch {
                        featureRuntime.clearEpisodeProgress(sourceId, episode.episodeId)
                    }
                },
                onClose = { selectedSeries = null },
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
    onQueryChanged: (String) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onFavoritesChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSeriesSelected: (SeriesSummary) -> Unit,
    onContinueEpisode: (SeriesEpisode) -> Unit,
    modifier: Modifier,
) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(series, key = { it.seriesId }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeriesSelected(item) },
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = if (selectedSeriesId == item.seriesId) 3.dp else 1.dp,
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

@Composable
private fun SeriesDetailsPane(
    selected: SeriesSummary,
    details: SeriesDetails?,
    loading: Boolean,
    error: SourceError?,
    selectedSeasonNumber: Int?,
    downloads: List<OfflineDownload>,
    onSeasonSelected: (Int) -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onPlay: (SeriesEpisode) -> Unit,
    onDownload: (SeriesEpisode) -> Unit,
    onClearProgress: (SeriesEpisode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onFavoriteChanged(!selected.isFavorite) }) {
                    Text(if (selected.isFavorite) "Unfavorite" else "Favorite")
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp))
            }
            error?.let {
                Text("Series details failed to load.", color = MaterialTheme.colorScheme.error)
            }
            details?.let { loaded ->
                loaded.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(loaded.seasons, key = { it.seasonId }) { season ->
                        FilterChip(
                            selected = selectedSeasonNumber == season.seasonNumber,
                            onClick = { onSeasonSelected(season.seasonNumber) },
                            label = { Text(season.name ?: "Season ${season.seasonNumber}") },
                        )
                    }
                }
                val selectedSeason = loaded.seasons.firstOrNull {
                    it.seasonNumber == selectedSeasonNumber
                } ?: loaded.seasons.firstOrNull()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selectedSeason?.episodes.orEmpty(), key = { it.episodeId }) { episode ->
                        val download = downloads.firstOrNull { item ->
                            item.mediaKind == DownloadMediaKinds.SERIES_EPISODE &&
                                item.contentId == episode.episodeId
                        }
                        EpisodeRow(
                            episode = episode,
                            download = download,
                            onPlay = { onPlay(episode) },
                            onDownload = { onDownload(episode) },
                            onClearProgress = { onClearProgress(episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeriesEpisode,
    download: OfflineDownload?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onClearProgress: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "E${episode.episodeNumber} · ${episode.title}",
                fontWeight = FontWeight.SemiBold,
            )
            episode.positionMs?.takeIf { it > 0L }?.let {
                Text(
                    if (episode.resumeAvailable) "Resume available" else "Watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onPlay) {
                    Text(if (episode.resumeAvailable) "Resume" else "Play")
                }
                Button(
                    onClick = onDownload,
                    enabled = download == null || download.state == DownloadStates.FAILED,
                ) {
                    Text(
                        when (download?.state) {
                            DownloadStates.QUEUED -> "Queued"
                            DownloadStates.DOWNLOADING -> "Downloading"
                            DownloadStates.COMPLETED -> "Downloaded"
                            DownloadStates.FAILED -> "Retry"
                            else -> "Download"
                        },
                    )
                }
                if ((episode.positionMs ?: 0L) > 0L) {
                    TextButton(onClick = onClearProgress) { Text("Clear") }
                }
            }
            if (download?.state == DownloadStates.DOWNLOADING) {
                val fraction = download.progressFraction
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            download?.failureReason?.takeIf { download.state == DownloadStates.FAILED }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
    val scope = rememberCoroutineScope()
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(Unit) {
        onFullscreenStateChanged(true)
        onDispose { onFullscreenStateChanged(false) }
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
                onClick = {
                    val view = playerView
                    scope.launch {
                        val player = view?.player
                        if (player != null) {
                            featureRuntime.saveEpisodeProgress(
                                sourceId = sourceId,
                                episodeId = episode.episodeId,
                                positionMs = player.currentPosition,
                                durationMs = player.duration.takeIf {
                                    it != C.TIME_UNSET && it > 0L
                                },
                            )
                        }
                        onExit()
                    }
                },
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
                        runtime.playbackVideoOutput.bind(view)
                        playerView = view
                    }
                },
                update = { view ->
                    runtime.playbackVideoOutput.bind(view)
                    playerView = view
                },
                onRelease = { view ->
                    runtime.playbackVideoOutput.unbind(view)
                    if (playerView === view) playerView = null
                },
            )
            if (playbackState is PlaybackState.Loading) {
                CircularProgressIndicator()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            when (playbackState) {
                is PlaybackState.Playing -> Button(onClick = runtime.playbackController::pause) {
                    Text("Pause")
                }
                is PlaybackState.Paused -> Button(onClick = runtime.playbackController::play) {
                    Text("Play")
                }
                is PlaybackState.Failed -> Button(onClick = runtime.playbackController::retry) {
                    Text("Retry")
                }
                else -> Spacer(Modifier.height(40.dp))
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
