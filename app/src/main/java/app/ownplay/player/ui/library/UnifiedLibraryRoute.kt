package app.ownplay.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.download.OfflineDownloadSpec
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.series.SeriesCatalog
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodCatalog
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val MISSING_FILE_REASON = "Downloaded file is missing"

private enum class UnifiedLibraryFilter {
    ALL,
    MOVIES,
    SERIES,
}

@Composable
internal fun UnifiedLibraryRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    onOpenMovieDetails: (sourceId: String, movieId: String) -> Unit,
    onOpenSeriesDetails: (sourceId: String, seriesId: String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadRuntime = remember(context) {
        OfflineDownloadFeatureRuntime(context.applicationContext)
    }
    val vodRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val seriesRuntime = remember(context) { SeriesFeatureRuntime(context.applicationContext) }

    DisposableEffect(downloadRuntime, vodRuntime, seriesRuntime) {
        onDispose {
            downloadRuntime.close()
            vodRuntime.close()
            seriesRuntime.close()
        }
    }

    val downloads by downloadRuntime.observeAll().collectAsState(initial = emptyList())
    val vodFlow = remember(sourceId, vodRuntime) {
        sourceId?.let(vodRuntime::observeCatalog) ?: flowOf(VodCatalog())
    }
    val seriesFlow = remember(sourceId, seriesRuntime) {
        sourceId?.let(seriesRuntime::observeCatalog) ?: flowOf(SeriesCatalog())
    }
    val vodCatalog by vodFlow.collectAsState(initial = VodCatalog())
    val seriesCatalog by seriesFlow.collectAsState(initial = SeriesCatalog())

    var filter by remember { mutableStateOf(UnifiedLibraryFilter.ALL) }
    var offlineOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var refreshing by remember(sourceId) { mutableStateOf(false) }
    var refreshWarning by remember(sourceId) { mutableStateOf(false) }
    var playbackSession by remember { mutableStateOf<LibraryPlaybackSession?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var selectedSeriesKey by remember { mutableStateOf<LibrarySeriesKey?>(null) }

    LaunchedEffect(sourceId, sourceKind) {
        if (sourceId == null || sourceKind != SourceKinds.XTREAM) return@LaunchedEffect
        refreshing = true
        refreshWarning = false
        val vodResult = vodRuntime.refresh(sourceId)
        val seriesResult = seriesRuntime.refresh(sourceId)
        refreshWarning = vodResult is SourceResult.Failure || seriesResult is SourceResult.Failure
        refreshing = false
    }

    val seriesGroups = remember(downloads) { groupLibrarySeries(downloads) }
    val selectedSeriesGroup = selectedSeriesKey?.let { key ->
        seriesGroups.firstOrNull { it.key == key }
    }

    LaunchedEffect(selectedSeriesKey, seriesGroups) {
        if (selectedSeriesKey != null && selectedSeriesGroup == null) {
            selectedSeriesKey = null
        }
    }

    val seriesBackOwner = remember { Any() }
    DisposableEffect(selectedSeriesKey, seriesBackOwner) {
        if (selectedSeriesKey != null) {
            PlaybackInteractionBridge.registerBackAction(seriesBackOwner) {
                selectedSeriesKey = null
            }
        }
        onDispose { PlaybackInteractionBridge.clearBackAction(seriesBackOwner) }
    }

    fun playDownload(download: OfflineDownload) {
        scope.launch {
            val request = downloadRuntime.playbackRequest(download.downloadId)
            if (request == null) {
                playbackError = "The offline file is unavailable. Download it again to restore offline playback."
                return@launch
            }
            val progress = downloadRuntime.playbackProgress(download.downloadId)
            playbackError = null
            runtime.playbackController.start(request)
            playbackSession = LibraryPlaybackSession(
                download = download,
                initialPositionMs = progress
                    ?.takeIf { !it.completed }
                    ?.positionMs
                    ?.coerceAtLeast(0L)
                    ?: 0L,
            )
        }
    }

    playbackSession?.let { session ->
        LibraryPlaybackScreen(
            runtime = runtime,
            session = session,
            onExit = {
                runtime.playbackController.stop()
                playbackSession = null
            },
            onProgress = { positionMs, durationMs ->
                scope.launch {
                    downloadRuntime.savePlaybackProgress(
                        downloadId = session.download.downloadId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                    )
                }
            },
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
        return
    }

    if (selectedSeriesGroup != null) {
        LibrarySeriesDetailScreen(
            group = selectedSeriesGroup,
            playbackError = playbackError,
            onBack = { selectedSeriesKey = null },
            onOpenFullSeries = selectedSeriesGroup.seriesId?.let { seriesId ->
                {
                    playbackError = null
                    onOpenSeriesDetails(selectedSeriesGroup.key.sourceId, seriesId)
                }
            },
            onDownloadEpisode = { episode ->
                scope.launch {
                    enqueueSeriesEpisode(
                        downloadRuntime = downloadRuntime,
                        sourceId = selectedSeriesGroup.key.sourceId,
                        episode = episode,
                    )
                }
            },
            onPlay = ::playDownload,
            onPause = { download -> scope.launch { downloadRuntime.pause(download.downloadId) } },
            onResume = { download -> scope.launch { downloadRuntime.resume(download.downloadId) } },
            onRetry = { download -> scope.launch { downloadRuntime.retry(download.downloadId) } },
            onRemove = { download -> scope.launch { downloadRuntime.remove(download.downloadId) } },
        )
        return
    }

    val normalizedQuery = query.trim().lowercase()
    val movieDownloadsByKey = remember(downloads) {
        downloads
            .filter { it.mediaKind == DownloadMediaKinds.MOVIE }
            .associateBy { "${it.sourceId}:${it.contentId}" }
    }
    val seriesGroupByIdentity = remember(seriesGroups) {
        seriesGroups.mapNotNull { group ->
            group.seriesId?.let { seriesId -> "${group.key.sourceId}:$seriesId" to group }
        }.toMap()
    }

    val visibleMovies = remember(vodCatalog.movies, sourceId, offlineOnly, normalizedQuery, movieDownloadsByKey) {
        if (sourceId == null) {
            emptyList()
        } else {
            vodCatalog.movies.filter { movie ->
                val download = movieDownloadsByKey["$sourceId:${movie.movieId}"]
                val offlineMatch = !offlineOnly || download?.countsForOfflineFilter() == true
                val queryMatch = normalizedQuery.isBlank() || movie.name.lowercase().contains(normalizedQuery)
                offlineMatch && queryMatch
            }
        }
    }
    val visibleSeries = remember(seriesCatalog.series, sourceId, offlineOnly, normalizedQuery, seriesGroupByIdentity) {
        if (sourceId == null) {
            emptyList()
        } else {
            seriesCatalog.series.filter { series ->
                val group = seriesGroupByIdentity["$sourceId:${series.seriesId}"]
                val offlineMatch = !offlineOnly || group?.episodes?.any(OfflineDownload::countsForOfflineFilter) == true
                val queryMatch = normalizedQuery.isBlank() || series.name.lowercase().contains(normalizedQuery)
                offlineMatch && queryMatch
            }
        }
    }

    val catalogMovieKeys = remember(vodCatalog.movies, sourceId) {
        if (sourceId == null) emptySet() else vodCatalog.movies.map { "$sourceId:${it.movieId}" }.toSet()
    }
    val orphanedOfflineMovies = remember(downloads, catalogMovieKeys, offlineOnly, normalizedQuery) {
        if (!offlineOnly) {
            emptyList()
        } else {
            downloads.filter { download ->
                download.mediaKind == DownloadMediaKinds.MOVIE &&
                    download.countsForOfflineFilter() &&
                    "${download.sourceId}:${download.contentId}" !in catalogMovieKeys &&
                    (normalizedQuery.isBlank() || download.title.lowercase().contains(normalizedQuery))
            }
        }
    }
    val catalogSeriesKeys = remember(seriesCatalog.series, sourceId) {
        if (sourceId == null) emptySet() else seriesCatalog.series.map { "$sourceId:${it.seriesId}" }.toSet()
    }
    val orphanedOfflineSeries = remember(seriesGroups, catalogSeriesKeys, offlineOnly, normalizedQuery) {
        if (!offlineOnly) {
            emptyList()
        } else {
            seriesGroups.filter { group ->
                val identity = group.seriesId?.let { "${group.key.sourceId}:$it" }
                group.episodes.any(OfflineDownload::countsForOfflineFilter) &&
                    (identity == null || identity !in catalogSeriesKeys) &&
                    (normalizedQuery.isBlank() || group.title.lowercase().contains(normalizedQuery))
            }
        }
    }

    val movieCount = if (filter == UnifiedLibraryFilter.SERIES) 0 else visibleMovies.size + orphanedOfflineMovies.size
    val seriesCount = if (filter == UnifiedLibraryFilter.MOVIES) 0 else visibleSeries.size + orphanedOfflineSeries.size
    val hasItems = movieCount + seriesCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (offlineOnly) {
                        "Offline media verified on this device"
                    } else {
                        "Movies and Series from your active playlist"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UnifiedLibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = {
                        Text(
                            when (option) {
                                UnifiedLibraryFilter.ALL -> "All"
                                UnifiedLibraryFilter.MOVIES -> "Movies"
                                UnifiedLibraryFilter.SERIES -> "Series"
                            },
                        )
                    },
                )
            }
            FilterChip(
                selected = offlineOnly,
                onClick = { offlineOnly = !offlineOnly },
                label = { Text("Offline") },
                leadingIcon = {
                    Icon(Icons.Filled.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = {
                Text(
                    when (filter) {
                        UnifiedLibraryFilter.ALL -> "Search Library"
                        UnifiedLibraryFilter.MOVIES -> "Search Movies"
                        UnifiedLibraryFilter.SERIES -> "Search Series"
                    },
                )
            },
            shape = RoundedCornerShape(12.dp),
        )

        if (refreshWarning && !offlineOnly) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                    Text(
                        text = "Some Library sections could not refresh. Showing the saved catalog.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        playbackError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!hasItems) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (offlineOnly) "Nothing available offline" else "No matching media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (offlineOnly) {
                            "Downloaded, downloading and paused media appears here when its local file is still present."
                        } else if (sourceKind != SourceKinds.XTREAM) {
                            "Movies and Series require an Xtream-compatible source."
                        } else {
                            "Try another Library filter or search term."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filter != UnifiedLibraryFilter.SERIES) {
                items(visibleMovies, key = { "catalog-movie:${it.movieId}" }) { movie ->
                    val movieSourceId = sourceId ?: return@items
                    UnifiedMovieCard(
                        movie = movie,
                        download = movieDownloadsByKey["$movieSourceId:${movie.movieId}"],
                        onOpen = { onOpenMovieDetails(movieSourceId, movie.movieId) },
                    )
                }
                items(orphanedOfflineMovies, key = { "offline-movie:${it.downloadId}" }) { download ->
                    OfflineOnlyMovieCard(
                        download = download,
                        onPlay = { playDownload(download) },
                        onRetry = { scope.launch { downloadRuntime.retry(download.downloadId) } },
                    )
                }
            }

            if (filter != UnifiedLibraryFilter.MOVIES) {
                items(visibleSeries, key = { "catalog-series:${it.seriesId}" }) { series ->
                    val seriesSourceId = sourceId ?: return@items
                    val group = seriesGroupByIdentity["$seriesSourceId:${series.seriesId}"]
                    UnifiedSeriesCard(
                        series = series,
                        group = group,
                        offlineMode = offlineOnly,
                        onOpen = {
                            if (offlineOnly && group != null) {
                                playbackError = null
                                selectedSeriesKey = group.key
                            } else {
                                onOpenSeriesDetails(seriesSourceId, series.seriesId)
                            }
                        },
                    )
                }
                items(orphanedOfflineSeries, key = { "offline-series:${it.key}" }) { group ->
                    LibrarySeriesCard(
                        group = group,
                        onOpenOfflineSeries = {
                            playbackError = null
                            selectedSeriesKey = group.key
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedMovieCard(
    movie: VodMovie,
    download: OfflineDownload?,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RemotePoster(
                url = movie.posterUrl,
                title = movie.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = movie.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = movieOfflineLabel(download),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    download == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    download.isMissingFile() -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UnifiedSeriesCard(
    series: SeriesSummary,
    group: LibrarySeriesGroup?,
    offlineMode: Boolean,
    onOpen: () -> Unit,
) {
    val managed = group?.episodes?.count(OfflineDownload::countsForOfflineFilter) ?: 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RemotePoster(
                url = series.posterUrl,
                title = series.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = series.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            series.description?.trim()?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = when {
                    managed > 0 -> "$managed episode${if (managed == 1) "" else "s"} managed offline"
                    offlineMode -> "Not available offline"
                    else -> "Series"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (managed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfflineOnlyMovieCard(
    download: OfflineDownload,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RemotePoster(
                url = download.posterUrl,
                title = download.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = download.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Offline copy · no longer in the active catalog",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            when (download.state) {
                DownloadStates.COMPLETED -> Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Play")
                }
                DownloadStates.FAILED -> Button(onClick = onRetry) { Text("Retry") }
                else -> Text(
                    text = movieOfflineLabel(download),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun OfflineDownload.countsForOfflineFilter(): Boolean =
    !isMissingFile() && when (state) {
        DownloadStates.COMPLETED,
        DownloadStates.DOWNLOADING,
        DownloadStates.QUEUED,
        DownloadStates.PAUSED,
        DownloadStates.FAILED,
        -> true
        else -> false
    }

private fun OfflineDownload.isMissingFile(): Boolean =
    state == DownloadStates.FAILED && failureReason == MISSING_FILE_REASON

private fun movieOfflineLabel(download: OfflineDownload?): String = when {
    download == null -> "Movie"
    download.isMissingFile() -> "File missing · Download again"
    download.state == DownloadStates.COMPLETED -> "Available offline"
    download.state == DownloadStates.DOWNLOADING -> "Downloading"
    download.state == DownloadStates.QUEUED -> "Queued for download"
    download.state == DownloadStates.PAUSED -> "Download paused"
    download.state == DownloadStates.FAILED -> download.failureReason ?: "Download failed"
    else -> "Movie"
}

private suspend fun enqueueSeriesEpisode(
    downloadRuntime: OfflineDownloadFeatureRuntime,
    sourceId: String,
    episode: SeriesEpisode,
) {
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
