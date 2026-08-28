package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
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
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import kotlinx.coroutines.launch

@Composable
internal fun DownloadsSettingsScreen(
    onBack: (() -> Unit)? = null,
    focusBackOnEntry: Boolean = false,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val horizontalPadding = if (isTelevision) 28.dp else 12.dp
    val verticalPadding = if (isTelevision) 16.dp else 8.dp
    val contentMaxWidth = if (isTelevision) 1080.dp else 840.dp
    val sectionSpacing = if (isTelevision) 12.dp else 8.dp
    val backFocusRequester = remember { FocusRequester() }
    val runtime = remember(context) {
        OfflineDownloadFeatureRuntime(context.applicationContext)
    }
    DisposableEffect(runtime) {
        onDispose { runtime.close() }
    }
    val downloads by runtime.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var removeError by remember { mutableStateOf<String?>(null) }
    var completedRemovalTarget by remember { mutableStateOf<OfflineDownload?>(null) }

    fun removeDownload(download: OfflineDownload) {
        scope.launch {
            removeError = if (runtime.remove(download.downloadId)) {
                null
            } else {
                "Could not remove the offline file. Check storage access and try again."
            }
        }
    }

    LaunchedEffect(isTelevision, focusBackOnEntry) {
        if (isTelevision && focusBackOnEntry && onBack != null) {
            backFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = contentMaxWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backFocusRequester),
                ) { Text("‹ Settings") }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Offline copies saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isTelevision) 10.dp else 6.dp),
                ) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Use Download from a movie or episode details screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        val readyCount = downloads.count { it.state == DownloadStates.COMPLETED }
        val activeCount = downloads.count {
            it.state == DownloadStates.QUEUED ||
                it.state == DownloadStates.DOWNLOADING ||
                it.state == DownloadStates.PAUSED
        }
        val storedBytes = downloads
            .asSequence()
            .filter { it.state == DownloadStates.COMPLETED }
            .sumOf(OfflineDownload::bytesDownloaded)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = contentMaxWidth)
                .align(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isTelevision) 18.dp else 12.dp,
                    vertical = if (isTelevision) 13.dp else 9.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTelevision) 12.dp else 8.dp),
            ) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = buildString {
                        append("$readyCount ready offline")
                        if (activeCount > 0) append(" · $activeCount active")
                        if (storedBytes > 0L) append(" · ${humanBytes(storedBytes)} stored")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        removeError?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = contentMaxWidth)
                .align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 12.dp else 8.dp),
        ) {
            items(downloads, key = { it.downloadId }) { download ->
                DownloadRow(
                    download = download,
                    spacious = isTelevision,
                    onPlayOffline = { DownloadPlaybackBridge.request(download) },
                    onPause = {
                        scope.launch { runtime.pause(download.downloadId) }
                    },
                    onResume = {
                        scope.launch { runtime.resume(download.downloadId) }
                    },
                    onRetry = {
                        scope.launch { runtime.retry(download.downloadId) }
                    },
                    onRemove = {
                        if (download.state == DownloadStates.COMPLETED) {
                            completedRemovalTarget = download
                        } else {
                            removeDownload(download)
                        }
                    },
                )
            }
        }
    }

    completedRemovalTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { completedRemovalTarget = null },
            title = { Text("Remove offline copy?") },
            text = {
                Text(
                    "${target.title} will be deleted from this device. " +
                        "You can download it again later.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        completedRemovalTarget = null
                        removeDownload(target)
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { completedRemovalTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DownloadRow(
    download: OfflineDownload,
    spacious: Boolean,
    onPlayOffline: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (spacious) 18.dp else 12.dp,
                vertical = if (spacious) 14.dp else 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (spacious) 10.dp else 7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (spacious) 12.dp else 9.dp),
            ) {
                Icon(
                    imageVector = if (download.mediaKind == DownloadMediaKinds.SERIES_EPISODE) {
                        Icons.Filled.VideoLibrary
                    } else {
                        Icons.Filled.Movie
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = downloadSecondaryLabel(download),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (download.state) {
                    DownloadStates.QUEUED,
                    DownloadStates.DOWNLOADING,
                    -> IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause download")
                    }
                    DownloadStates.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume download")
                    }
                    DownloadStates.COMPLETED -> TextButton(onClick = onPlayOffline) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text("Play Offline")
                    }
                    DownloadStates.FAILED -> IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry download")
                    }
                    else -> Unit
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove download")
                }
            }

            when (download.state) {
                DownloadStates.DOWNLOADING -> {
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
                        text = "Downloading · ${humanBytes(download.bytesDownloaded)}" +
                            download.totalBytes?.let { " / ${humanBytes(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadStates.QUEUED -> Text(
                    text = "Queued",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DownloadStates.PAUSED -> Text(
                    text = "Paused · ${humanBytes(download.bytesDownloaded)}" +
                        download.totalBytes?.let { " / ${humanBytes(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DownloadStates.COMPLETED -> Text(
                    text = "Offline ready · ${downloadStorageLabel(download)} · ${humanBytes(download.bytesDownloaded)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                DownloadStates.FAILED -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = download.failureReason ?: "Download failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun downloadSecondaryLabel(download: OfflineDownload): String {
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

private fun downloadStorageLabel(download: OfflineDownload): String =
    if (download.savedToDownloads) "Device Downloads" else "OwnPlay private storage"

private fun humanBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1_073_741_824L -> "%.1f GB".format(safe / 1_073_741_824.0)
        safe >= 1_048_576L -> "%.1f MB".format(safe / 1_048_576.0)
        safe >= 1_024L -> "%.1f KB".format(safe / 1_024.0)
        else -> "$safe B"
    }
}
