package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection

private sealed interface OwnPlayRoute {
    data object Sources : OwnPlayRoute

    data class Live(
        val sourceId: String,
    ) : OwnPlayRoute

    data class Playback(
        val selection: LivePlaybackSelection,
    ) : OwnPlayRoute
}

@Composable
fun OwnPlayApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
) {
    val sources by runtime.observeSources().collectAsState(initial = emptyList())
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()
    var route by remember { mutableStateOf<OwnPlayRoute>(OwnPlayRoute.Sources) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }

    LaunchedEffect(sources, route) {
        val routedSourceId = when (val current = route) {
            OwnPlayRoute.Sources -> null
            is OwnPlayRoute.Live -> current.sourceId
            is OwnPlayRoute.Playback -> current.selection.request.sourceId
        }
        if (routedSourceId != null && sources.none { source -> source.sourceId == routedSourceId }) {
            route = OwnPlayRoute.Sources
        }
    }

    when (val current = route) {
        OwnPlayRoute.Sources -> SourcePickerScreen(
            sources = sources,
            activeSelection = activeSelection,
            playbackState = playbackState,
            onSourceSelected = { sourceId -> route = OwnPlayRoute.Live(sourceId) },
            onResumePlayback = { selection -> route = OwnPlayRoute.Playback(selection) },
        )

        is OwnPlayRoute.Live -> LiveRoute(
            runtime = runtime,
            sourceId = current.sourceId,
            activeSelection = activeSelection,
            playbackState = playbackState,
            onBackToSources = { route = OwnPlayRoute.Sources },
            onPlaybackRequested = { selection ->
                activeSelection = selection
                runtime.playbackController.start(selection.request)
                route = OwnPlayRoute.Playback(selection)
            },
            onResumePlayback = { selection -> route = OwnPlayRoute.Playback(selection) },
        )

        is OwnPlayRoute.Playback -> PlaybackScreen(
            selection = current.selection,
            state = playbackState,
            trackState = playbackTrackState,
            videoOutput = runtime.playbackVideoOutput,
            onPlay = runtime.playbackController::play,
            onPause = runtime.playbackController::pause,
            onRetry = runtime.playbackController::retry,
            onAudioSelection = runtime.playbackTrackController::selectAudio,
            onSubtitleSelection = runtime.playbackTrackController::selectSubtitle,
            onNavigate = { direction ->
                current.selection.navigate(direction)?.let { targetSelection ->
                    activeSelection = targetSelection
                    runtime.playbackController.start(targetSelection.request)
                    route = OwnPlayRoute.Playback(targetSelection)
                }
            },
            onReturnToChannels = {
                route = OwnPlayRoute.Live(current.selection.request.sourceId)
            },
            onFullscreenStateChanged = onPlaybackFullscreenChanged,
        )
    }
}
