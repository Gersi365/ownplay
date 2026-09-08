package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.livePlaybackPresentationSession
import app.ownplay.player.onDemandPresentationSession
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.LivePlaybackSurfaceTeardown
import app.ownplay.player.playback.LivePlaybackTransitionGate
import app.ownplay.player.playback.LivePlaybackTransitionTarget
import app.ownplay.player.playback.OnDemandContentKind
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.selection.ActivePlaylistSelection
import app.ownplay.player.source.selection.ActivePlaylistStore
import app.ownplay.player.source.selection.resolveActivePlaylistId
import app.ownplay.player.ui.home.TvHomeScreen
import app.ownplay.player.ui.series.SeriesRoute
import app.ownplay.player.ui.shell.TvDestination
import app.ownplay.player.ui.shell.TvMediaShell
import app.ownplay.player.ui.shell.defaultTvDestination
import app.ownplay.player.ui.vod.VodRoute
import kotlinx.coroutines.launch

/**
 * TV-only OwnPlay presentation shell.
 *
 * Primary navigation is Home / Live TV / Movies / Series / Settings. Home is a dedicated TV-first
 * cache-backed presentation; playback, source, persistence, and Live session ownership remain in
 * their established runtimes.
 */
@Composable
internal fun TVOwnPlayApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    TVConfigurationBoundary {
        TVOwnPlayAppContent(
            runtime = runtime,
            onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
            onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
            onLivePreviewActiveChanged = onLivePreviewActiveChanged,
        )
    }
}

@Composable
private fun TVOwnPlayAppContent(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activePlaylistStore = remember(context) {
        ActivePlaylistStore(context.applicationContext)
    }
    val activePlaylistSelection by activePlaylistStore.observe().collectAsState(
        initial = ActivePlaylistSelection.Loading,
    )
    val activePlaylistScope = rememberCoroutineScope()
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
    val syncState by runtime.sourceSyncState.collectAsState()
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()
    val livePresentation by runtime.livePlaybackPresentationSession.state.collectAsState()
    val onDemandPresentation by runtime.onDemandPresentationSession.state.collectAsState()

    var destination by remember {
        mutableStateOf(
            when {
                onDemandPresentation.kind == OnDemandContentKind.MOVIE -> TvDestination.MOVIES
                onDemandPresentation.kind == OnDemandContentKind.SERIES -> TvDestination.SERIES
                livePresentation.selection != null -> TvDestination.LIVE_TV
                else -> defaultTvDestination
            },
        )
    }
    var activeSourceId by remember { mutableStateOf(onDemandPresentation.sourceId) }
    var requestedVodMovieId by remember {
        mutableStateOf(
            onDemandPresentation.itemId.takeIf {
                onDemandPresentation.kind == OnDemandContentKind.MOVIE
            },
        )
    }
    var requestedSeriesId by remember {
        mutableStateOf(
            onDemandPresentation.itemId.takeIf {
                onDemandPresentation.kind == OnDemandContentKind.SERIES
            },
        )
    }
    var movieDetailReturnToHome by remember {
        mutableStateOf(
            onDemandPresentation.kind == OnDemandContentKind.MOVIE &&
                onDemandPresentation.returnToLibraryOnDetailBack,
        )
    }
    var seriesDetailReturnToHome by remember {
        mutableStateOf(
            onDemandPresentation.kind == OnDemandContentKind.SERIES &&
                onDemandPresentation.returnToLibraryOnDetailBack,
        )
    }
    var homeReturnFocusKey by remember { mutableStateOf<String?>(null) }
    var homeReturnFocusGeneration by remember { mutableIntStateOf(0) }
    var homeReturnFocusPending by remember { mutableStateOf(false) }
    val vodFullscreen = onDemandPresentation.isMoviePlayback
    val seriesFullscreen = onDemandPresentation.isSeriesPlayback
    val activeSelection = livePresentation.selection
    val fullscreenSelection = livePresentation.fullscreenSelection
    val liveTransitionGate = remember { LivePlaybackTransitionGate() }

    fun rememberActiveSource(sourceId: String?) {
        activeSourceId = sourceId
        activePlaylistScope.launch {
            activePlaylistStore.set(sourceId)
        }
    }

    fun stopLivePresentation(clearPresentation: () -> Unit) {
        LivePlaybackSurfaceTeardown.stopAfterDetaching(
            detachCurrentSurface = {
                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)
            },
            stopPlayback = runtime.playbackController::stop,
            clearPresentation = clearPresentation,
        )
    }

    fun openLiveFullscreen(selection: LivePlaybackSelection) {
        liveTransitionGate.requestHandoff(
            target = LivePlaybackTransitionTarget.fullscreen(selection),
            detachCurrentSurface = {
                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)
            },
            stopPlayback = runtime.playbackController::stop,
            switchPresentation = {
                runtime.livePlaybackPresentationSession.showFullscreen(selection)
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    fun returnLiveToPreview(selection: LivePlaybackSelection) {
        liveTransitionGate.requestHandoff(
            target = LivePlaybackTransitionTarget.preview(selection),
            detachCurrentSurface = {
                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)
            },
            stopPlayback = runtime.playbackController::stop,
            switchPresentation = {
                rememberActiveSource(selection.request.sourceId)
                destination = TvDestination.LIVE_TV
                runtime.livePlaybackPresentationSession.showPreview(selection)
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    fun openDestination(target: TvDestination) {
        if (target != TvDestination.LIVE_TV && activeSelection != null) {
            stopLivePresentation {
                runtime.livePlaybackPresentationSession.clear()
            }
        }

        val onDemandCurrent = runtime.onDemandPresentationSession.current
        when (target) {
            TvDestination.MOVIES -> {
                if (onDemandCurrent.kind != OnDemandContentKind.MOVIE) {
                    activeSourceId?.let(runtime.onDemandPresentationSession::showMovieCatalog)
                }
            }
            TvDestination.SERIES -> {
                if (onDemandCurrent.kind != OnDemandContentKind.SERIES) {
                    activeSourceId?.let(runtime.onDemandPresentationSession::showSeriesCatalog)
                }
            }
            else -> if (onDemandCurrent.kind != null) {
                runtime.onDemandPresentationSession.clear()
            }
        }

        if (target != TvDestination.MOVIES) {
            requestedVodMovieId = null
            movieDetailReturnToHome = false
        }
        if (target != TvDestination.SERIES) {
            requestedSeriesId = null
            seriesDetailReturnToHome = false
        }
        if (target == TvDestination.HOME && !homeReturnFocusPending) {
            homeReturnFocusKey = null
        }
        destination = target
    }

    BackHandler(enabled = destination != TvDestination.HOME) {
        val interactionHandled = when (destination) {
            TvDestination.MOVIES,
            TvDestination.SERIES,
            -> PlaybackInteractionBridge.handleBack()
            TvDestination.HOME,
            TvDestination.LIVE_TV,
            TvDestination.SETTINGS,
            -> false
        }
        if (interactionHandled) return@BackHandler

        openDestination(TvDestination.HOME)
    }

    LaunchedEffect(summaries, activePlaylistSelection) {
        val persistedSelection = activePlaylistSelection as? ActivePlaylistSelection.Ready
            ?: return@LaunchedEffect
        val enabledSourceIds = summaries
            .asSequence()
            .filter { summary -> summary.enabled }
            .map { summary -> summary.sourceId }
            .toList()
        val previousSourceId = activeSourceId
        val resolvedSourceId = resolveActivePlaylistId(
            persistedSourceId = persistedSelection.sourceId,
            currentSourceId = activeSourceId,
            enabledSourceIds = enabledSourceIds,
        )
        activeSourceId = resolvedSourceId

        if (enabledSourceIds.isNotEmpty() && persistedSelection.sourceId != resolvedSourceId) {
            activePlaylistStore.set(resolvedSourceId)
        }
        if (resolvedSourceId != null && previousSourceId != resolvedSourceId) {
            runtime.onActiveSourceSelected(resolvedSourceId)
        }

        val selectionSourceId = activeSelection?.request?.sourceId
        if (selectionSourceId != null && selectionSourceId != resolvedSourceId) {
            stopLivePresentation {
                runtime.livePlaybackPresentationSession.clear()
            }
        }
        val onDemandSourceId = runtime.onDemandPresentationSession.current.sourceId
        if (
            enabledSourceIds.isNotEmpty() &&
            resolvedSourceId != null &&
            onDemandSourceId != null &&
            onDemandSourceId != resolvedSourceId
        ) {
            runtime.onDemandPresentationSession.clear()
        }
        if (resolvedSourceId == null || previousSourceId != resolvedSourceId) {
            homeReturnFocusKey = null
            homeReturnFocusPending = false
        }
        if (resolvedSourceId == null) {
            requestedVodMovieId = null
            requestedSeriesId = null
            movieDetailReturnToHome = false
            seriesDetailReturnToHome = false
        }
    }

    val previewActive =
        destination == TvDestination.LIVE_TV &&
            activeSelection != null &&
            fullscreenSelection == null
    val playbackSurfaceActive =
        previewActive ||
            fullscreenSelection != null ||
            vodFullscreen ||
            seriesFullscreen
    val observedLiveTransitionTarget =
        fullscreenSelection?.let(LivePlaybackTransitionTarget::fullscreen)
            ?: if (previewActive) {
                activeSelection?.let(LivePlaybackTransitionTarget::preview)
            } else {
                null
            }

    SideEffect {
        liveTransitionGate.reconcileObserved(observedLiveTransitionTarget)
    }

    LaunchedEffect(playbackSurfaceActive) {
        onPlaybackSurfaceActiveChanged(playbackSurfaceActive)
    }
    LaunchedEffect(previewActive) {
        // TV never opts into rotation-driven fullscreen; keep the activity callback explicitly off.
        onLivePreviewActiveChanged(false)
    }
    LaunchedEffect(fullscreenSelection != null) {
        onPlaybackFullscreenChanged(fullscreenSelection != null)
    }

    val openedFullscreen = fullscreenSelection
    if (openedFullscreen != null) {
        PlaybackScreen(
            selection = openedFullscreen,
            state = playbackState,
            trackState = playbackTrackState,
            videoOutput = runtime.playbackVideoOutput,
            onPlay = runtime.playbackController::play,
            onPause = runtime.playbackController::pause,
            onRetry = runtime.playbackController::retry,
            onAudioSelection = runtime.playbackTrackController::selectAudio,
            onSubtitleSelection = runtime.playbackTrackController::selectSubtitle,
            onNavigate = { direction ->
                (fullscreenSelection ?: openedFullscreen)
                    .navigate(direction)
                    ?.let { target ->
                        runtime.livePlaybackPresentationSession.replaceSelection(target)
                        runtime.playbackController.start(target.request)
                    }
            },
            onReturnToChannels = {
                returnLiveToPreview(
                    fullscreenSelection ?: activeSelection ?: openedFullscreen,
                )
            },
            onFullscreenStateChanged = {},
        )
        return
    }

    val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId && it.enabled }
    val hideNavigationRail =
        previewActive ||
            vodFullscreen ||
            seriesFullscreen

    TvMediaShell(
        activeDestination = destination,
        railVisible = !hideNavigationRail,
        onDestinationActivated = ::openDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (destination) {
            TvDestination.HOME -> TvHomeScreen(
                sourceId = activeSourceId,
                sourceKind = activeSummary?.sourceKind,
                returnFocusKey = homeReturnFocusKey.takeIf { homeReturnFocusPending },
                returnFocusGeneration = homeReturnFocusGeneration,
                onReturnFocusConsumed = {
                    homeReturnFocusPending = false
                    homeReturnFocusKey = null
                },
                onOpenMovieDetails = { sourceId, movieId, focusKey ->
                    homeReturnFocusKey = focusKey
                    homeReturnFocusPending = false
                    rememberActiveSource(sourceId)
                    runtime.onDemandPresentationSession.showMovieDetail(
                        sourceId = sourceId,
                        movieId = movieId,
                        returnToLibraryOnDetailBack = true,
                    )
                    requestedVodMovieId = movieId
                    movieDetailReturnToHome = true
                    openDestination(TvDestination.MOVIES)
                },
                onOpenSeriesDetails = { sourceId, seriesId, focusKey ->
                    homeReturnFocusKey = focusKey
                    homeReturnFocusPending = false
                    rememberActiveSource(sourceId)
                    runtime.onDemandPresentationSession.showSeriesDetail(
                        sourceId = sourceId,
                        seriesId = seriesId,
                        returnToLibraryOnDetailBack = true,
                    )
                    requestedSeriesId = seriesId
                    seriesDetailReturnToHome = true
                    openDestination(TvDestination.SERIES)
                },
                onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )

            TvDestination.LIVE_TV -> {
                val sourceId = activeSourceId
                if (sourceId == null) {
                    TVNoSourceScreen(
                        syncState = syncState,
                        onAddPlaylist = { openDestination(TvDestination.SETTINGS) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LiveRoute(
                        runtime = runtime,
                        sourceId = sourceId,
                        activeSelection = activeSelection,
                        playbackState = playbackState,
                        videoOutput = runtime.playbackVideoOutput,
                        syncState = syncState,
                        onPlay = runtime.playbackController::play,
                        onPause = runtime.playbackController::pause,
                        onRetry = runtime.playbackController::retry,
                        onOpenMovies = { openDestination(TvDestination.MOVIES) },
                        onOpenSeries = { openDestination(TvDestination.SERIES) },
                        onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                        onPreviewRequested = { selection ->
                            runtime.livePlaybackPresentationSession.showPreview(selection)
                            runtime.playbackController.start(selection.request)
                        },
                        onPreviewClosed = {
                            stopLivePresentation {
                                runtime.livePlaybackPresentationSession.clear()
                            }
                        },
                        onOpenFullscreen = { selection ->
                            openLiveFullscreen(activeSelection ?: selection)
                        },
                        onNavigatePreview = { direction ->
                            activeSelection
                                ?.navigate(direction)
                                ?.let { target ->
                                    runtime.livePlaybackPresentationSession.replaceSelection(target)
                                    runtime.playbackController.start(target.request)
                                }
                        },
                    )
                }
            }

            TvDestination.MOVIES -> VodRoute(
                runtime = runtime,
                sourceId = activeSourceId,
                sourceKind = activeSummary?.sourceKind,
                requestedMovieId = requestedVodMovieId,
                onRequestedMovieConsumed = { requestedVodMovieId = null },
                returnToLibraryOnDetailBack = movieDetailReturnToHome,
                onReturnToLibrary = {
                    if (movieDetailReturnToHome && homeReturnFocusKey != null) {
                        homeReturnFocusGeneration += 1
                        homeReturnFocusPending = true
                    }
                    openDestination(TvDestination.HOME)
                },
                onOpenLive = { openDestination(TvDestination.LIVE_TV) },
                onOpenSeries = { openDestination(TvDestination.SERIES) },
                onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                onFullscreenStateChanged = onPlaybackFullscreenChanged,
            )

            TvDestination.SERIES -> SeriesRoute(
                runtime = runtime,
                sourceId = activeSourceId,
                sourceKind = activeSummary?.sourceKind,
                requestedSeriesId = requestedSeriesId,
                onRequestedSeriesConsumed = { requestedSeriesId = null },
                returnToLibraryOnDetailBack = seriesDetailReturnToHome,
                onReturnToLibrary = {
                    if (seriesDetailReturnToHome && homeReturnFocusKey != null) {
                        homeReturnFocusGeneration += 1
                        homeReturnFocusPending = true
                    }
                    openDestination(TvDestination.HOME)
                },
                onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                onFullscreenStateChanged = onPlaybackFullscreenChanged,
            )

            TvDestination.SETTINGS -> SettingsScreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                activeSourceName = activeSummary?.name,
                hasActivePlayback =
                    activeSelection != null ||
                        vodFullscreen ||
                        seriesFullscreen,
                onOpenLive = { openDestination(TvDestination.LIVE_TV) },
                onOpenSourceInLive = { sourceId ->
                    if (sourceId != activeSourceId && activeSelection != null) {
                        stopLivePresentation {
                            runtime.livePlaybackPresentationSession.clear()
                        }
                    }
                    rememberActiveSource(sourceId)
                    runtime.onDemandPresentationSession.clear()
                    destination = TvDestination.LIVE_TV
                },
                onStopPlayback = {
                    if (activeSelection != null || fullscreenSelection != null) {
                        stopLivePresentation {
                            runtime.livePlaybackPresentationSession.clear()
                        }
                    } else {
                        runtime.playbackController.stop()
                        runtime.onDemandPresentationSession.clear()
                    }
                },
            )
        }
    }
}

@Composable
private fun TVNoSourceScreen(
    syncState: SourceSyncState,
    onAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (syncState.sourceId != null) "Loading Live TV…" else "No playlist configured",
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onAddPlaylist) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun TVConfigurationBoundary(content: @Composable () -> Unit) {
    val current = LocalConfiguration.current
    val tvConfiguration = Configuration(current).apply {
        orientation = Configuration.ORIENTATION_LANDSCAPE
        uiMode =
            (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                Configuration.UI_MODE_TYPE_TELEVISION
    }
    CompositionLocalProvider(
        LocalConfiguration provides tvConfiguration,
        content = content,
    )
}
