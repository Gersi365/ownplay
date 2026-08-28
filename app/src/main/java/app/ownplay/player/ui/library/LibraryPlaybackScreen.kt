package app.ownplay.player.ui.library

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.playbackStatusLabel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class LibraryPlaybackSession(
    val download: OfflineDownload,
    val initialPositionMs: Long,
)

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    session: LibraryPlaybackSession,
    onExit: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long?) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    backContentDescription: String = "Back to Library",
    contextLabel: String = "Library",
) {
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackControls = PlaybackPresentationPolicy.controlsFor(playbackState)
    val failedState = playbackState as? PlaybackState.Failed
    val playbackContextLabel = "$contextLabel · " + if (session.download.savedToDownloads) {
        "Phone Downloads"
    } else {
        "OwnPlay private storage"
    }
    val sessionMediaKind = when (session.download.mediaKind) {
        DownloadMediaKinds.MOVIE -> PlaybackMediaKind.MOVIE
        DownloadMediaKinds.SERIES_EPISODE -> PlaybackMediaKind.SERIES_EPISODE
        else -> null
    }
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val backOwner = remember(session.download.downloadId) { Any() }
    val backFocusRequester = remember(session.download.downloadId) { FocusRequester() }
    var playerView by remember(session.download.downloadId) { mutableStateOf<PlayerView?>(null) }
    var currentPosition by remember(session.download.downloadId) {
        mutableStateOf(session.initialPositionMs)
    }
    var duration by remember(session.download.downloadId) { mutableStateOf(0L) }
    var resumeApplied by remember(session.download.downloadId) { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    DisposableEffect(session.download.downloadId, backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, onExit)
        onDispose {
            if (currentPosition > 0L) {
                onProgress(currentPosition, duration.takeIf { it > 0L })
            }
            sessionMediaKind?.let { mediaKind ->
                runtime.playbackController.stopIfCurrent(
                    sourceId = session.download.sourceId,
                    channelId = session.download.contentId,
                    mediaKind = mediaKind,
                )
            }
            PlaybackInteractionBridge.clearBackAction(backOwner)
            onFullscreenStateChanged(false)
        }
    }

    LaunchedEffect(isTelevision, playerView, playbackState, session.download.downloadId) {
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
            val player = playerView?.player ?: return@LaunchedEffect
            if (player.currentPosition <= 5_000L) {
                session.initialPositionMs.takeIf { it > 5_000L }?.let { position ->
                    player.seekTo(position)
                    currentPosition = position
                }
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
                IconButton(
                    modifier = Modifier.focusRequester(backFocusRequester),
                    onClick = onExit,
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = backContentDescription, tint = Color.White)
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
                        text = playbackContextLabel,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(82.dp))
            }

            if (playbackControls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (failedState != null) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(playbackStatusLabel(failedState), color = Color.White)
                        if (playbackControls.canRetry) {
                            FilledTonalButton(onClick = runtime.playbackController::retry) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
