package app.ownplay.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackAudioSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackSubtitleSelection
import app.ownplay.player.playback.PlaybackTrackState
import app.ownplay.player.playback.PlaybackVideoOutput
import kotlinx.coroutines.CancellationException

@Composable
internal fun LiveFullscreenPlaybackScreen(
    runtime: OwnPlayAppRuntime,
    selection: LivePlaybackSelection,
    state: PlaybackState,
    trackState: PlaybackTrackState,
    videoOutput: PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onAudioSelection: (PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (PlaybackSubtitleSelection) -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onReturnToChannels: () -> Unit,
) {
    val sourceId = selection.request.sourceId
    val channelId = selection.request.channelId
    var epgSnapshot by remember(sourceId, channelId) { mutableStateOf<EpgSnapshot?>(null) }
    var epgLoading by remember(sourceId, channelId) { mutableStateOf(false) }
    var epgFailed by remember(sourceId, channelId) { mutableStateOf(false) }
    var showEpgGuide by remember(sourceId, channelId) { mutableStateOf(false) }

    LaunchedEffect(sourceId, channelId) {
        epgSnapshot = null
        epgLoading = true
        epgFailed = false
        try {
            epgSnapshot = runtime.epgSnapshot(
                sourceId = sourceId,
                channelId = channelId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            epgSnapshot = null
            epgFailed = true
        } finally {
            epgLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlaybackScreen(
            selection = selection,
            state = state,
            trackState = trackState,
            videoOutput = videoOutput,
            onPlay = onPlay,
            onPause = onPause,
            onRetry = onRetry,
            onAudioSelection = onAudioSelection,
            onSubtitleSelection = onSubtitleSelection,
            onNavigate = onNavigate,
            onReturnToChannels = onReturnToChannels,
            onFullscreenStateChanged = {},
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
        ) {
            EpgPanel(
                snapshot = epgSnapshot,
                loading = epgLoading,
                failed = epgFailed,
                onOpenGuide = { showEpgGuide = true },
            )
        }
    }

    if (showEpgGuide) {
        EpgGuideSheet(
            channelName = selection.displayName,
            snapshot = epgSnapshot,
            loading = epgLoading,
            failed = epgFailed,
            onDismiss = { showEpgGuide = false },
        )
    }
}
