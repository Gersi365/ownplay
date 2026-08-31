package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LiveFullscreenEntryReason
import app.ownplay.player.playback.LivePlaybackPresentationPolicy
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.LivePlaybackSurfaceTeardown
import app.ownplay.player.playback.LivePlaybackTransitionGate
import app.ownplay.player.playback.LivePlaybackTransitionTarget
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.ui.library.UnifiedLibraryRoute
import app.ownplay.player.ui.series.SeriesRoute
import app.ownplay.player.ui.vod.VodRoute

private enum class MobileSection {
    LIVE,
    LIBRARY,
    MOVIES,
    SERIES,
    SETTINGS,
}

/**
 * Mobile-only OwnPlay presentation shell.
 *
 * Primary navigation is always Live / Library / Settings. Movies and Series are internal Library
 * routes, never primary destinations. Navigation remains at the bottom in portrait and landscape.
 */
@Composable
internal fun MobileOwnPlayApp(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    MobileConfigurationBoundary {
        MobileOwnPlayAppContent(
            runtime = runtime,
            rotationFullscreenEnabled = rotationFullscreenEnabled,
            onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
            onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
            onLivePreviewActiveChanged = onLivePreviewActiveChanged,
        )
    }
}

@Composable
private fun MobileOwnPlayAppContent(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
    val syncState by runtime.sourceSyncState.collectAsState()
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackOrigin by runtime.playbackController.resolvedOrigin.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()

    var section by remember { mutableStateOf(MobileSection.LIVE) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenEntryReason by remember { mutableStateOf<LiveFullscreenEntryReason?>(null) }
    var requestedVodMovieId by remember { mutableStateOf<String?>(null) }
    var requestedSeriesId by remember { mutableStateOf<String?>(null) }
    var movieDetailReturnToLibrary by remember { mutableStateOf(false) }
    var seriesDetailReturnToLibrary by remember { mutableStateOf(false) }
    var vodFullscreen by remember { mutableStateOf(false) }
    var seriesFullscreen by remember { mutableStateOf(false) }
    var libraryFullscreen by remember { mutableStateOf(false) }
    val liveTransitionGate = remember { LivePlaybackTransitionGate() }

    fun stopLivePresentation(clearPresentation: () -> Unit) {
        LivePlaybackSurfaceTeardown.stopAfterDetaching(
            detachCurrentSurface = {
                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)
            },
            stopPlayback = runtime.playbackController::stop,
            clearPresentation = clearPresentation,
        )
    }

    fun openLiveFullscreen(
        selection: LivePlaybackSelection,
        reason: LiveFullscreenEntryReason,
    ) {
        liveTransitionGate.requestHandoff(
            target = LivePlaybackTransitionTarget.fullscreen(selection),
            detachCurrentSurface = {
                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)
            },
            stopPlayback = runtime.playbackController::stop,
            switchPresentation = {
                activeSelection = selection
                fullscreenEntryReason = reason
                fullscreenSelection = selection
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
                activeSourceId = selection.request.sourceId
                section = MobileSection.LIVE
                activeSelection = selection
                fullscreenSelection = null
                fullscreenEntryReason = null
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    fun openSection(target: MobileSection) {
        if (target != MobileSection.LIVE && activeSelection != null) {
            stopLivePresentation {
                activeSelection = null
                fullscreenSelection = null
                fullscreenEntryReason = null
            }
        }
        if (target != MobileSection.MOVIES) {
            requestedVodMovieId = null
            movieDetailReturnToLibrary = false
        }
        if (target != MobileSection.SERIES) {
            requestedSeriesId = null
            seriesDetailReturnToLibrary = false
        }
        section = target
    }

    LaunchedEffect(summaries) {
        val ids = summaries.map { it.sourceId }.toSet()
        activeSourceId = when {
            activeSourceId in ids -> activeSourceId
            summaries.isNotEmpty() -> summaries.first().sourceId
            else -> null
        }
        val selectionSourceId = activeSelection?.request?.sourceId
        if (selectionSourceId != null && selectionSourceId !in ids) {
            stopLivePresentation {
                activeSelection = null
                fullscreenSelection = null
                fullscreenEntryReason = null
            }
        }
        if (activeSourceId !in ids) {
            requestedVodMovieId = null
            requestedSeriesId = null
            movieDetailReturnToLibrary = false
            seriesDetailReturnToLibrary = false
        }
    }

    val previewActive =
        section == MobileSection.LIVE &&
            activeSelection != null &&
            fullscreenSelection == null
    val playbackSurfaceActive =
        previewActive ||
            fullscreenSelection != null ||
            vodFullscreen ||
            seriesFullscreen ||
            libraryFullscreen
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
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
    LaunchedEffect(previewActive, rotationFullscreenEnabled) {
        onLivePreviewActiveChanged(previewActive && rotationFullscreenEnabled)
    }
    LaunchedEffect(fullscreenSelection != null) {
        onPlaybackFullscreenChanged(fullscreenSelection != null)
    }

    LaunchedEffect(
        rotationFullscreenEnabled,
        isLandscape,
        isPortrait,
        activeSelection?.request?.channelId,
        fullscreenSelection?.request?.channelId,
        fullscreenEntryReason,
    ) {
        val selected = activeSelection
        if (
            LivePlaybackPresentationPolicy.shouldEnterFullscreenFromRotation(
                rotationFullscreenEnabled = rotationFullscreenEnabled,
                isLandscape = isLandscape,
                hasSelection = selected != null,
                alreadyFullscreen = fullscreenSelection != null,
            )
        ) {
            selected?.let { openLiveFullscreen(it, LiveFullscreenEntryReason.ROTATION) }
            return@LaunchedEffect
        }

        val opened = fullscreenSelection
        if (
            LivePlaybackPresentationPolicy.shouldReturnToPreviewFromRotation(
                rotationFullscreenEnabled = rotationFullscreenEnabled,
                isPortrait = isPortrait,
                entryReason = fullscreenEntryReason,
                isFullscreen = opened != null,
            )
        ) {
            opened?.let(::returnLiveToPreview)
        }
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
                        activeSelection = target
                        fullscreenSelection = target
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

    val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId }
    val librarySectionActive =
        section == MobileSection.LIBRARY ||
            section == MobileSection.MOVIES ||
            section == MobileSection.SERIES
    val hidePrimaryNavigation = vodFullscreen || seriesFullscreen || libraryFullscreen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hidePrimaryNavigation) {
                MobilePrimaryNavigationBar(
                    liveSelected = section == MobileSection.LIVE,
                    librarySelected = librarySectionActive,
                    settingsSelected = section == MobileSection.SETTINGS,
                    onOpenLive = { openSection(MobileSection.LIVE) },
                    onOpenLibrary = { openSection(MobileSection.LIBRARY) },
                    onOpenSettings = { openSection(MobileSection.SETTINGS) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            when (section) {
                MobileSection.LIVE -> {
                    val sourceId = activeSourceId
                    if (sourceId == null) {
                        MobileNoSourceScreen(
                            syncState = syncState,
                            onAddPlaylist = { openSection(MobileSection.SETTINGS) },
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
                            onOpenMovies = { openSection(MobileSection.MOVIES) },
                            onOpenSeries = { openSection(MobileSection.SERIES) },
                            onOpenSettings = { openSection(MobileSection.SETTINGS) },
                            onPreviewRequested = { selection ->
                                activeSelection = selection
                                runtime.playbackController.start(selection.request)
                            },
                            onPreviewClosed = {
                                stopLivePresentation { activeSelection = null }
                            },
                            onOpenFullscreen = { selection ->
                                openLiveFullscreen(
                                    selection = activeSelection ?: selection,
                                    reason = LiveFullscreenEntryReason.USER,
                                )
                            },
                            onNavigatePreview = { direction ->
                                activeSelection
                                    ?.navigate(direction)
                                    ?.let { target ->
                                        activeSelection = target
                                        runtime.playbackController.start(target.request)
                                    }
                            },
                        )
                    }
                }

                MobileSection.LIBRARY -> UnifiedLibraryRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    onOpenMovieDetails = { sourceId, movieId ->
                        activeSourceId = sourceId
                        requestedVodMovieId = movieId
                        movieDetailReturnToLibrary = true
                        openSection(MobileSection.MOVIES)
                    },
                    onOpenSeriesDetails = { sourceId, seriesId ->
                        activeSourceId = sourceId
                        requestedSeriesId = seriesId
                        seriesDetailReturnToLibrary = true
                        openSection(MobileSection.SERIES)
                    },
                    onFullscreenStateChanged = { fullscreen ->
                        libraryFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                MobileSection.MOVIES -> VodRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    requestedMovieId = requestedVodMovieId,
                    onRequestedMovieConsumed = { requestedVodMovieId = null },
                    returnToLibraryOnDetailBack = movieDetailReturnToLibrary,
                    onReturnToLibrary = { openSection(MobileSection.LIBRARY) },
                    onOpenLive = { openSection(MobileSection.LIVE) },
                    onOpenSeries = { openSection(MobileSection.SERIES) },
                    onOpenSettings = { openSection(MobileSection.SETTINGS) },
                    onFullscreenStateChanged = { fullscreen ->
                        vodFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                MobileSection.SERIES -> SeriesRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    requestedSeriesId = requestedSeriesId,
                    onRequestedSeriesConsumed = { requestedSeriesId = null },
                    returnToLibraryOnDetailBack = seriesDetailReturnToLibrary,
                    onReturnToLibrary = { openSection(MobileSection.LIBRARY) },
                    onOpenSettings = { openSection(MobileSection.SETTINGS) },
                    onFullscreenStateChanged = { fullscreen ->
                        seriesFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                MobileSection.SETTINGS -> SettingsScreen(
                    runtime = runtime,
                    summaries = summaries,
                    syncState = syncState,
                    activeSourceName = activeSummary?.name,
                    hasActivePlayback =
                        activeSelection != null ||
                            vodFullscreen ||
                            seriesFullscreen ||
                            libraryFullscreen,
                    onOpenLive = { openSection(MobileSection.LIVE) },
                    onOpenSourceInLive = { sourceId ->
                        if (sourceId != activeSourceId && activeSelection != null) {
                            stopLivePresentation {
                                activeSelection = null
                                fullscreenSelection = null
                                fullscreenEntryReason = null
                            }
                        }
                        activeSourceId = sourceId
                        section = MobileSection.LIVE
                    },
                    onStopPlayback = {
                        if (activeSelection != null || fullscreenSelection != null) {
                            stopLivePresentation {
                                activeSelection = null
                                fullscreenSelection = null
                                fullscreenEntryReason = null
                            }
                        } else {
                            runtime.playbackController.stop()
                        }
                    },
                )
            }

            val resolvedOrigin = playbackOrigin
            if (
                resolvedOrigin != null &&
                (vodFullscreen || seriesFullscreen || libraryFullscreen)
            ) {
                PlaybackOriginBadge(origin = resolvedOrigin)
            }
        }
    }
}

@Composable
private fun MobilePrimaryNavigationBar(
    liveSelected: Boolean,
    librarySelected: Boolean,
    settingsSelected: Boolean,
    onOpenLive: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = liveSelected,
                onClick = onOpenLive,
                icon = { Icon(Icons.Filled.LiveTv, contentDescription = "Live") },
                label = { Text("Live") },
                colors = colors,
            )
            NavigationBarItem(
                selected = librarySelected,
                onClick = onOpenLibrary,
                icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = "Library") },
                label = { Text("Library") },
                colors = colors,
            )
            NavigationBarItem(
                selected = settingsSelected,
                onClick = onOpenSettings,
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                colors = colors,
            )
        }
    }
}

@Composable
private fun MobileNoSourceScreen(
    syncState: SourceSyncState,
    onAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
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
private fun MobileConfigurationBoundary(content: @Composable () -> Unit) {
    val current = LocalConfiguration.current
    val mobileConfiguration = Configuration(current).apply {
        uiMode =
            (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                Configuration.UI_MODE_TYPE_NORMAL
    }
    CompositionLocalProvider(
        LocalConfiguration provides mobileConfiguration,
        content = content,
    )
}
