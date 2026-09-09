package app.ownplay.player.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.OnDemandPlaybackSurface
import app.ownplay.player.vod.VodFeatureRuntime
import app.ownplay.player.vod.VodMovie
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TV_MOVIE_EXIT_PROGRESS_SAVE_TIMEOUT_MILLIS = 1_000L

/**
 * TV-only Movie fullscreen adapter.
 *
 * Playback ownership stays in the established shared controller/video output. This adapter owns
 * Movie resume/progress persistence and routes presentation through the shared on-demand surface,
 * whose TV branch provides OwnPlay remote-first transient controls.
 */
@Composable
internal fun TvMoviePlaybackRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    movie: VodMovie,
    onExit: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val featureRuntime = remember(context) { VodFeatureRuntime(context.applicationContext) }
    val playbackState by runtime.playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val backOwner = remember(movie.movieId) { Any() }
    var playerView by remember(movie.movieId) { mutableStateOf<PlayerView?>(null) }
    var currentPosition by remember(movie.movieId) { mutableStateOf(movie.positionMs ?: 0L) }
    var duration by remember(movie.movieId) { mutableStateOf(movie.durationMs ?: 0L) }
    var resumeApplied by remember(movie.movieId) { mutableStateOf(false) }
    var exitRequested by remember(movie.movieId) { mutableStateOf(false) }

    fun exitPlayback() {
        if (exitRequested) return
        exitRequested = true
        val lastPosition = currentPosition
        val lastDuration = duration.takeIf { it > 0L }
        scope.launch {
            withTimeoutOrNull(TV_MOVIE_EXIT_PROGRESS_SAVE_TIMEOUT_MILLIS) {
                featureRuntime.saveProgress(sourceId, movie.movieId, lastPosition, lastDuration)
            }
            runtime.playbackController.stopIfCurrent(
                sourceId = sourceId,
                channelId = movie.movieId,
                mediaKind = PlaybackMediaKind.MOVIE,
            )
            onFullscreenStateChanged(false)
            onExit()
        }
    }

    DisposableEffect(featureRuntime) {
        onDispose { featureRuntime.close() }
    }

    DisposableEffect(movie.movieId, backOwner) {
        onFullscreenStateChanged(true)
        PlaybackInteractionBridge.registerBackAction(backOwner, ::exitPlayback)
        onDispose {
            PlaybackInteractionBridge.clearBackAction(backOwner)
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
            val player = playerView?.player ?: return@LaunchedEffect
            val resumePosition = movie.positionMs
                ?.takeIf { it > 5_000L && !movie.progressCompleted }
            if (resumePosition != null && player.currentPosition < 1_000L) {
                player.seekTo(resumePosition)
                currentPosition = resumePosition
            } else {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.takeIf { it > 0L } ?: duration
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

    OnDemandPlaybackSurface(
        runtime = runtime,
        contentKey = "movie:${movie.movieId}",
        title = movie.name,
        playbackState = playbackState,
        currentPositionMs = currentPosition,
        durationMs = duration,
        exitRequested = exitRequested,
        onExit = ::exitPlayback,
        onPlayerViewAvailable = { playerView = it },
        onPlayerViewReleased = { released ->
            if (playerView === released) playerView = null
        },
        onSeekPositionChanged = { currentPosition = it },
    )
}
