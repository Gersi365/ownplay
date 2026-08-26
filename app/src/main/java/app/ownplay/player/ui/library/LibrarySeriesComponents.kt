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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.download.OfflineDownloadSpec
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.series.SeriesDetails
import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesFeatureRuntime
import app.ownplay.player.source.SourceResult
import app.ownplay.player.ui.vod.RemotePoster
import kotlinx.coroutines.launch

@Composable
internal fun LibrarySeriesCard(
    group: LibrarySeriesGroup,
    onOpenSeries: () -> Unit,
    onOpenOfflineEpisodes: () -> Unit,
) {
    val completed = group.episodes.count { it.state == DownloadStates.COMPLETED }
    val active = group.episodes.count {
        it.state == DownloadStates.DOWNLOADING || it.state == DownloadStates.QUEUED
    }
    val paused = group.episodes.count { it.state == DownloadStates.PAUSED }
    val failed = group.episodes.count { it.state == DownloadStates.FAILED }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSeries),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box {
                RemotePoster(
                    url = group.posterUrl,
                    title = group.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = group.episodeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${group.episodeCount} episode")
                    if (group.episodeCount != 1) append("s")
                    if (group.seasonCount > 0) {
                        append(" · ${group.seasonCount} season")
                        if (group.seasonCount != 1) append("s")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = seriesTransferSummary(
                    completed = completed,
                    active = active,
                    paused = paused,
                    failed = failed,
                    totalBytes = group.totalBytesDownloaded,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (failed > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onOpenOfflineEpisodes,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text("Offline episodes")
            }
        }
    }
}

@Composable
internal fun LibrarySeriesDetailScreen(
    group: LibrarySeriesGroup,
    playbackError: String?,
    onBack: () -> Unit,
    onPlay: (OfflineDownload) -> Unit,
    onPause: (OfflineDownload) -> Unit,
    onResume: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onRemove: (OfflineDownload) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seriesRuntime = remember(context) {
        SeriesFeatureRuntime(context.applicationContext)
    }
    val directDownloadRuntime = remember(context) {
        OfflineDownloadFeatureRuntime(context.applicationContext)
    }
    DisposableEffect(seriesRuntime) {
        onDispose { seriesRuntime.close() }
    }
    DisposableEffect(directDownloadRuntime) {
        onDispose { directDownloadRuntime.close() }
    }

    val seriesId = group.seriesId
    var showAll by remember(group.key) { mutableStateOf(false) }
    var fullDetails by remember(group.key) { mutableStateOf<SeriesDetails?>(null) }
    var catalogLoading by remember(group.key) { mutableStateOf(false) }
    var catalogLoadFailed by remember(group.key) { mutableStateOf(false) }
    var retryNonce by remember(group.key) { mutableIntStateOf(0) }
    var selectedSeason by remember(group.key) { mutableStateOf<Int?>(null) }

    LaunchedEffect(showAll, seriesId, retryNonce) {
        if (!showAll || seriesId == null || fullDetails != null) return@LaunchedEffect
        catalogLoading = true
        catalogLoadFailed = false
        when (val result = seriesRuntime.details(group.key.sourceId, seriesId)) {
            is SourceResult.Success -> fullDetails = result.value
            is SourceResult.Failure -> catalogLoadFailed = true
        }
        catalogLoading = false
    }

    val managedByEpisodeId = remember(group.episodes) {
        group.episodes.associateBy { it.contentId }
    }
    val seasonStatuses = remember(showAll, fullDetails, group.episodes) {
        if (showAll && fullDetails != null) {
            fullDetails.orEmptySeasons().map { season ->
                LibrarySeasonStatus(
                    seasonNumber = season.seasonNumber,
                    managedCount = season.episodes.count { episode ->
                        managedByEpisodeId.containsKey(episode.episodeId)
                    },
                    totalCount = season.episodes.size,
                )
            }
        } else {
            group.seasonNumbers.map { seasonNumber ->
                val count = group.episodes.count { it.seasonNumber == seasonNumber }
                LibrarySeasonStatus(
                    seasonNumber = seasonNumber,
                    managedCount = count,
                    totalCount = count,
                )
            }
        }
    }

    LaunchedEffect(showAll, seasonStatuses) {
        val currentSeason = selectedSeason
        if (currentSeason != null && seasonStatuses.none { it.seasonNumber == currentSeason }) {
            selectedSeason = null
        }
    }

    val visibleManagedEpisodes = remember(group.episodes, selectedSeason) {
        if (selectedSeason == null) {
            group.episodes
        } else {
            group.episodes.filter { it.seasonNumber == selectedSeason }
        }
    }
    val visibleCatalogEpisodes = remember(showAll, fullDetails, selectedSeason) {
        if (!showAll || fullDetails == null) {
            emptyList()
        } else {
            fullDetails.orEmptySeasons()
                .asSequence()
                .filter { season -> selectedSeason == null || season.seasonNumber == selectedSeason }
                .flatMap { it.episodes.asSequence() }
                .sortedWith(compareBy<SeriesEpisode>({ it.seasonNumber }, { it.episodeNumber }, { it.title.lowercase() }))
                .toList()
        }
    }
    val totalCatalogEpisodes = fullDetails
        ?.orEmptySeasons()
        ?.sumOf { it.episodes.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Library")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (showAll && totalCatalogEpisodes != null) {
                        "${group.episodeCount} in Library · $totalCatalogEpisodes total"
                    } else {
                        "${group.episodeCount} episode${if (group.episodeCount == 1) "" else "s"} in Library"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (seriesId != null) {
                TextButton(
                    onClick = {
                        showAll = !showAll
                        selectedSeason = null
                    },
                ) {
                    Text(if (showAll) "Downloaded only" else "Show all")
                }
            }
            Icon(
                Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        if (catalogLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading complete series catalog…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showAll && catalogLoadFailed) {
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
                        text = "The full Series catalog could not be loaded. Downloads remain available.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            catalogLoadFailed = false
                            retryNonce += 1
                        },
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        if (seasonStatuses.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedSeason == null,
                        onClick = { selectedSeason = null },
                        label = { Text("All") },
                    )
                }
                items(seasonStatuses, key = { it.seasonNumber }) { season ->
                    FilterChip(
                        selected = selectedSeason == season.seasonNumber,
                        onClick = { selectedSeason = season.seasonNumber },
                        label = {
                            Text(
                                if (showAll && fullDetails != null) {
                                    "Season ${season.seasonNumber} · ${season.managedCount}/${season.totalCount}"
                                } else {
                                    "Season ${season.seasonNumber}"
                                },
                            )
                        },
                        modifier = if (showAll && season.managedCount == 0) {
                            Modifier.alpha(0.55f)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        playbackError?.let { message ->
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
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showAll && fullDetails != null) {
                items(visibleCatalogEpisodes, key = { it.episodeId }) { episode ->
                    val managed = managedByEpisodeId[episode.episodeId]
                    if (managed != null) {
                        LibraryEpisodeRow(
                            download = managed,
                            onPlay = { onPlay(managed) },
                            onPause = { onPause(managed) },
                            onResume = { onResume(managed) },
                            onRetry = { onRetry(managed) },
                            onRemove = { onRemove(managed) },
                        )
                    } else {
                        LibraryMissingEpisodeRow(
                            episode = episode,
                            onDownload = {
                                scope.launch {
                                    directDownloadRuntime.enqueue(
                                        OfflineDownloadSpec(
                                            sourceId = group.key.sourceId,
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
                            },
                        )
                    }
                }
            } else {
                items(visibleManagedEpisodes, key = { it.downloadId }) { download ->
                    LibraryEpisodeRow(
                        download = download,
                        onPlay = { onPlay(download) },
                        onPause = { onPause(download) },
                        onResume = { onResume(download) },
                        onRetry = { onRetry(download) },
                        onRemove = { onRemove(download) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryMissingEpisodeRow(
    episode: SeriesEpisode,
    onDownload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(0.58f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "S${episode.seasonNumber} · E${episode.episodeNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Not downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Download")
            }
        }
    }
}

@Composable
private fun LibraryEpisodeRow(
    download: OfflineDownload,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = episodeLabel(download),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove episode download")
                }
            }

            when (download.state) {
                DownloadStates.COMPLETED -> {
                    Text(
                        text = "Downloaded · ${humanBytes(download.bytesDownloaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (download.savedToDownloads) {
                            "Phone Downloads · available offline"
                        } else {
                            "OwnPlay private storage · available offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DownloadStates.DOWNLOADING,
                DownloadStates.QUEUED,
                DownloadStates.PAUSED,
                -> {
                    val progress = download.progressFraction
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = downloadProgressLabel(download),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DownloadStates.FAILED -> Text(
                    text = download.failureReason ?: "Download failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (download.state) {
                    DownloadStates.COMPLETED -> Button(
                        onClick = onPlay,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Play", maxLines = 1, softWrap = false)
                    }

                    DownloadStates.DOWNLOADING,
                    DownloadStates.QUEUED,
                    -> FilledTonalButton(
                        onClick = onPause,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }

                    DownloadStates.PAUSED -> FilledTonalButton(
                        onClick = onResume,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume")
                    }

                    DownloadStates.FAILED -> FilledTonalButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private data class LibrarySeasonStatus(
    val seasonNumber: Int,
    val managedCount: Int,
    val totalCount: Int,
)

private fun SeriesDetails?.orEmptySeasons() = this?.seasons.orEmpty()

private fun episodeLabel(download: OfflineDownload): String {
    return listOfNotNull(
        download.seasonNumber?.let { "S$it" },
        download.episodeNumber?.let { "E$it" },
    ).joinToString(" · ").ifBlank { "Episode" }
}

private fun seriesTransferSummary(
    completed: Int,
    active: Int,
    paused: Int,
    failed: Int,
    totalBytes: Long,
): String {
    val parts = buildList {
        if (completed > 0) add("$completed downloaded")
        if (active > 0) add("$active downloading")
        if (paused > 0) add("$paused paused")
        if (failed > 0) add("$failed failed")
    }
    val state = parts.joinToString(" · ").ifBlank { "No completed episodes" }
    return "$state · ${humanBytes(totalBytes)}"
}

private fun downloadProgressLabel(download: OfflineDownload): String {
    val downloaded = humanBytes(download.bytesDownloaded)
    val total = download.totalBytes?.takeIf { it > 0L }?.let(::humanBytes)
    val state = when (download.state) {
        DownloadStates.PAUSED -> "Paused"
        DownloadStates.QUEUED -> "Queued"
        else -> "Downloading"
    }
    return if (total == null) "$state · $downloaded" else "$state · $downloaded / $total"
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
