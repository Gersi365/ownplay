package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LiveFullscreenEntryReason
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.LivePlaybackSurfaceTeardown
import app.ownplay.player.playback.LivePlaybackTransitionGate
import app.ownplay.player.playback.LivePlaybackTransitionTarget
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.selection.ActivePlaylistSelection
import app.ownplay.player.source.selection.ActivePlaylistStore
import app.ownplay.player.source.selection.resolveActivePlaylistId
import app.ownplay.player.ui.library.UnifiedLibraryRoute
import app.ownplay.player.ui.series.SeriesRoute
import app.ownplay.player.ui.vod.VodRoute
import kotlinx.coroutines.launch

private enum class TVSection {
    LIVE,
    LIBRARY,
    MOVIES,
    SERIES,
    SETTINGS,
}

/**
 * TV-only OwnPlay presentation shell.
 *
 * Primary navigation exposes Live / Library / Settings only. Movies and Series are internal
 * Library routes. The TV configuration boundary makes D-pad/TV presentation deterministic and
 * prevents shared presentation components from exposing Mobile-only Offline/Download UI.
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

    var section by remember { mutableStateOf(TVSection.LIVE) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var requestedVodMovieId by remember { mutableStateOf<String?>(null) }
    var requestedSeriesId by remember { mutableStateOf<String?>(null) }
    var movieDetailReturnToLibrary by remember { mutableStateOf(false) }
    var seriesDetailReturnToLibrary by remember { mutableStateOf(false) }
    var vodFullscreen by remember { mutableStateOf(false) }
    var seriesFullscreen by remember { mutableStateOf(false) }
    var libraryFullscreen by remember { mutableStateOf(false) }
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
                activeSelection = selection
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
                rememberActiveSource(selection.request.sourceId)
                section = TVSection.LIVE
                activeSelection = selection
                fullscreenSelection = null
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    fun openSection(target: TVSection) {
        if (target != TVSection.LIVE && activeSelection != null) {
            stopLivePresentation {
                activeSelection = null
                fullscreenSelection = null
            }
        }
        if (target != TVSection.MOVIES) {
            requestedVodMovieId = null
            movieDetailReturnToLibrary = false
        }
        if (target != TVSection.SERIES) {
            requestedSeriesId = null
            seriesDetailReturnToLibrary = false
        }
        section = target
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
                activeSelection = null
                fullscreenSelection = null
            }
        }
        if (resolvedSourceId == null) {
            requestedVodMovieId = null
            requestedSeriesId = null
            movieDetailReturnToLibrary = false
            seriesDetailReturnToLibrary = false
        }
    }

    val previewActive =
        section == TVSection.LIVE &&
            activeSelection != null &&
            fullscreenSelection == null
    val playbackSurfaceActive =
        previewActive ||
            fullscreenSelection != null ||
            vodFullscreen ||
            seriesFullscreen ||
            libraryFullscreen
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

    val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId && it.enabled }
    val librarySectionActive =
        section == TVSection.LIBRARY ||
            section == TVSection.MOVIES ||
            section == TVSection.SERIES
    val hidePrimaryNavigation = vodFullscreen || seriesFullscreen || libraryFullscreen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!hidePrimaryNavigation) {
                TVPrimaryNavigationBar(
                    liveSelected = section == TVSection.LIVE,
                    librarySelected = librarySectionActive,
                    settingsSelected = section == TVSection.SETTINGS,
                    onOpenLive = { openSection(TVSection.LIVE) },
                    onOpenLibrary = { openSection(TVSection.LIBRARY) },
                    onOpenSettings = { openSection(TVSection.SETTINGS) },
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
                TVSection.LIVE -> {
                    val sourceId = activeSourceId
                    if (sourceId == null) {
                        TVNoSourceScreen(
                            syncState = syncState,
                            onAddPlaylist = { openSection(TVSection.SETTINGS) },
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
                            onOpenMovies = { openSection(TVSection.MOVIES) },
                            onOpenSeries = { openSection(TVSection.SERIES) },
                            onOpenSettings = { openSection(TVSection.SETTINGS) },
                            onPreviewRequested = { selection ->
                                activeSelection = selection
                                runtime.playbackController.start(selection.request)
                            },
                            onPreviewClosed = {
                                stopLivePresentation { activeSelection = null }
                            },
                            onOpenFullscreen = { selection ->
                                openLiveFullscreen(activeSelection ?: selection)
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

                TVSection.LIBRARY -> UnifiedLibraryRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    onOpenMovieDetails = { sourceId, movieId ->
                        rememberActiveSource(sourceId)
                        requestedVodMovieId = movieId
                        movieDetailReturnToLibrary = true
                        openSection(TVSection.MOVIES)
                    },
                    onOpenSeriesDetails = { sourceId, seriesId ->
                        rememberActiveSource(sourceId)
                        requestedSeriesId = seriesId
                        seriesDetailReturnToLibrary = true
                        openSection(TVSection.SERIES)
                    },
                    onFullscreenStateChanged = { fullscreen ->
                        libraryFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                TVSection.MOVIES -> VodRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    requestedMovieId = requestedVodMovieId,
                    onRequestedMovieConsumed = { requestedVodMovieId = null },
                    returnToLibraryOnDetailBack = movieDetailReturnToLibrary,
                    onReturnToLibrary = { openSection(TVSection.LIBRARY) },
                    onOpenLive = { openSection(TVSection.LIVE) },
                    onOpenSeries = { openSection(TVSection.SERIES) },
                    onOpenSettings = { openSection(TVSection.SETTINGS) },
                    onFullscreenStateChanged = { fullscreen ->
                        vodFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                TVSection.SERIES -> SeriesRoute(
                    runtime = runtime,
                    sourceId = activeSourceId,
                    sourceKind = activeSummary?.sourceKind,
                    requestedSeriesId = requestedSeriesId,
                    onRequestedSeriesConsumed = { requestedSeriesId = null },
                    returnToLibraryOnDetailBack = seriesDetailReturnToLibrary,
                    onReturnToLibrary = { openSection(TVSection.LIBRARY) },
                    onOpenSettings = { openSection(TVSection.SETTINGS) },
                    onFullscreenStateChanged = { fullscreen ->
                        seriesFullscreen = fullscreen
                        onPlaybackFullscreenChanged(fullscreen)
                    },
                )

                TVSection.SETTINGS -> SettingsScreen(
                    runtime = runtime,
                    summaries = summaries,
                    syncState = syncState,
                    activeSourceName = activeSummary?.name,
                    hasActivePlayback =
                        activeSelection != null ||
                            vodFullscreen ||
                            seriesFullscreen ||
                            libraryFullscreen,
                    onOpenLive = { openSection(TVSection.LIVE) },
                    onOpenSourceInLive = { sourceId ->
                        if (sourceId != activeSourceId && activeSelection != null) {
                            stopLivePresentation {
                                activeSelection = null
                                fullscreenSelection = null
                            }
                        }
                        activeSourceId = sourceId
                        section = TVSection.LIVE
                    },
                    onStopPlayback = {
                        if (activeSelection != null || fullscreenSelection != null) {
                            stopLivePresentation {
                                activeSelection = null
                                fullscreenSelection = null
                            }
                        } else {
                            runtime.playbackController.stop()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TVPrimaryNavigationBar(
    liveSelected: Boolean,
    librarySelected: Boolean,
    settingsSelected: Boolean,
    onOpenLive: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TVPrimaryNavigationItem(
                label = "Live",
                icon = Icons.Filled.LiveTv,
                selected = liveSelected,
                onClick = onOpenLive,
            )
            TVPrimaryNavigationItem(
                label = "Library",
                icon = Icons.Filled.VideoLibrary,
                selected = librarySelected,
                onClick = onOpenLibrary,
            )
            TVPrimaryNavigationItem(
                label = "Settings",
                icon = Icons.Filled.Settings,
                selected = settingsSelected,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun TVPrimaryNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val emphasized = selected || focused

    Surface(
        modifier = Modifier
            .widthIn(min = 132.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    focused -> MaterialTheme.colorScheme.onPrimaryContainer
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
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
