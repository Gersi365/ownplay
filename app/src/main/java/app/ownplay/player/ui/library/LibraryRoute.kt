package app.ownplay.player.ui.library

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.vod.RemotePoster
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class LibraryFilter {
    ALL,
    MOVIES,
    SERIES,
}

private data class LibraryPlaybackSession(
    val download: OfflineDownload,
    val initialPositionMs: Long,
)

@Composable
internal fun LibraryRoute(
    runtime: OwnPlayAppRuntime,
    onOpenMovieDetails: (sourceId: String, movieId: String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val downloadRuntime = remember(context) {
        OfflineDownloadFeatureRuntime(context.applicationContext)
    }
    DisposableEffect(downloadRuntime) {
        onDispose { downloadRuntime.close() }
    }

    val downloads by downloadRuntime.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var playbackSession by remember { mutableStateOf<LibraryPlaybackSession?>(null) }
    var playbackRequestError by remember { mutableStateOf<String?>(null) }

    val session = playbackSession
    if (session != null) {
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

    val filtered = downloads.filter { download ->
        when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.MOVIES -> download.mediaKind == DownloadMediaKinds.MOVIE
            LibraryFilter.SERIES -> download.mediaKind == DownloadMediaKinds.SERIES_EPISODE
        }
    }

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
                    text = "New downloads are saved to your phone Downloads folder by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = {
                        Text(
                            when (option) {
                                LibraryFilter.ALL -> "All"
                                LibraryFilter.MOVIES -> "Movies"
                                LibraryFilter.SERIES -> "Series"
                            },
                        )
                    },
                )
            }
        }

        playbackRequestError?.let { message ->
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

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
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
                        text = if (downloads.isEmpty()) "No downloads yet" else "No items in this filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Downloaded movies and episodes appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { it.downloadId }) { download ->
                LibraryMediaCard(
                    download = download,
                    onOpenDetails = if (download.mediaKind == DownloadMediaKinds.MOVIE) {
                        {
                            onOpenMovieDetails(download.sourceId, download.contentId)
                        }
                    } else {
                        null
                    },
                    onPlayOffline = {
                        scope.launch {
                            val request = downloadRuntime.playbackRequest(download.downloadId)
                            if (request == null) {
                                playbackRequestError =
                                    "The downloaded file is unavailable. Remove it or download it again."
                                return@launch
                            }
                            val progress = downloadRuntime.playbackProgress(download.downloadId)
                            playbackRequestError = null
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
                    },
                    onPause = {
                        scope.launch { downloadRuntime.pause(download.downloadId) }
                    },
                    onResume = {
                        scope.launch { downloadRuntime.resume(download.downloadId) }
                    },
                    onRetry = {
                        scope.launch { downloadRuntime.retry(download.downloadId) }
                    },
                    onRemove = {
                        scope.launch { downloadRuntime.remove(download.downloadId) }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryMediaCard(
    download: OfflineDownload,
    onOpenDetails: (() -> Unit)?,
    onPlayOffline: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val detailsModifier = if (onOpenDetails == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onOpenDetails)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            RemotePoster(
                url = download.posterUrl,
                title = download.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(detailsModifier),
            )

            Text(
                text = download.title,
                modifier = detailsModifier,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = librarySecondaryLabel(download),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

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
                    if (download.savedToDownloads) {
                        Text(
                            text = "Saving to phone Downloads",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        onClick = onPlayOffline,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Play Offline",
                            maxLines = 1,
                            softWrap = false,
                        )
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

                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove download")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LibraryPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    session: LibraryPlaybackSession,
    onExit: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long?) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val backOwner = remember(session.download.downloadId) { Any() }
    var playerView by remember(session.download.downloadId) { mutableStateOf<PlayerView?>(null) }
    var currentPosition by remember(session.download.downloadId) {
        mutableStateOf(session.initialPositionMs)
    }
    var duration by remember(session.download.downloadId) { mutableStateOf(0L) }
    var resumeApplied by remember(session.download.downloadId) { mutableStateOf(false) }

    DisposableEffect(session.download.downloadId, backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, onExit)
        onDispose {
            if (currentPosition > 0L) {
                onProgress(currentPosition, duration.takeIf { it > 0L })
            }
            PlaybackInteractionBridge.clearBackAction(backOwner)
            onFullscreenStateChanged(false)
        }
    }

    LaunchedEffect(playbackState, playerView, session.download.downloadId) {
        val request = when (val state = playbackState) {
            is PlaybackState.Playing -> state.request
            is PlaybackState.Paused -> state.request
            else -> null
        }
        if (
            !resumeApplied &&
            request != null &&
            request.sourceId == session.download.sourceId &&
            request.channelId == session.download.contentId
        ) {
            session.initialPositionMs.takeIf { it > 5_000L }?.let { position ->
                playerView?.player?.seekTo(position)
                currentPosition = position
            }
            resumeApplied = true
        }
    }

    LaunchedEffect(playerView, session.download.downloadId) {
        var saveTick = 0
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            val player = playerView?.player ?: continue
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: duration
            saveTick += 1
            if (saveTick >= 5 && currentPosition > 0L) {
                saveTick = 0
                onProgress(currentPosition, duration.takeIf { it > 0L })
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
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        runtime.playbackVideoOutput.bind(this)
                        playerView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.useController = true
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
                    .background(Color.Black.copy(alpha = 0.66f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Library", tint = Color.White)
                }
                Icon(
                    imageVector = if (session.download.mediaKind == DownloadMediaKinds.SERIES_EPISODE) {
                        Icons.Filled.VideoLibrary
                    } else {
                        Icons.Filled.Movie
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.download.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Library · offline copy",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(82.dp))
            }

            when (playbackState) {
                is PlaybackState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                is PlaybackState.Failed -> Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Playback failed", color = Color.White)
                        FilledTonalButton(onClick = runtime.playbackController::retry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

private fun librarySecondaryLabel(download: OfflineDownload): String {
    if (download.mediaKind != DownloadMediaKinds.SERIES_EPISODE) return "Movie"
    val episode = listOfNotNull(
        download.seasonNumber?.let { "S$it" },
        download.episodeNumber?.let { "E$it" },
    ).joinToString(" · ")
    return listOfNotNull(
        download.seriesTitle?.takeIf(String::isNotBlank),
        episode.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { "Series episode" }
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
