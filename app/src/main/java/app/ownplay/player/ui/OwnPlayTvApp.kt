package app.ownplay.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.LivePlaybackSurfaceTeardown
import app.ownplay.player.playback.LivePlaybackTransitionGate
import app.ownplay.player.playback.LivePlaybackTransitionTarget
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.ui.library.TvLibraryMovieDetailRoute
import app.ownplay.player.ui.library.TvLibraryRoute
import app.ownplay.player.ui.library.TvLibrarySeriesDetailRoute
import app.ownplay.player.ui.live.TvLiveRoute

internal enum class TvOwnPlaySection {
    HOME,
    LIVE,
    LIBRARY,
    SETTINGS,
    MOVIE_DETAILS,
    SERIES_DETAILS,
}

internal fun tvPrimarySectionBackDestination(
    section: TvOwnPlaySection,
): TvOwnPlaySection? = when (section) {
    TvOwnPlaySection.LIVE,
    TvOwnPlaySection.LIBRARY,
    TvOwnPlaySection.SETTINGS,
    -> TvOwnPlaySection.HOME

    TvOwnPlaySection.HOME,
    TvOwnPlaySection.MOVIE_DETAILS,
    TvOwnPlaySection.SERIES_DETAILS,
    -> null
}

internal enum class TvLiveRemoteArrow {
    UP,
    DOWN,
    OTHER,
}

internal enum class TvLiveRemoteInputOwner {
    CHANNELS,
    PLAYBACK_CONTROLS,
    TRACK_SELECTION,
    DIAGNOSTICS,
    DIALOG,
}

internal fun tvLiveRemoteNavigation(
    arrow: TvLiveRemoteArrow,
    keyDown: Boolean,
    inputOwner: TvLiveRemoteInputOwner = TvLiveRemoteInputOwner.CHANNELS,
): PlaybackNavigationDirection? {
    if (!keyDown || inputOwner != TvLiveRemoteInputOwner.CHANNELS) return null
    return when (arrow) {
        TvLiveRemoteArrow.UP -> PlaybackNavigationDirection.PREVIOUS
        TvLiveRemoteArrow.DOWN -> PlaybackNavigationDirection.NEXT
        TvLiveRemoteArrow.OTHER -> null
    }
}

@Composable
internal fun OwnPlayTvApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit = {},
    onLivePreviewActiveChanged: (Boolean) -> Unit = {},
) {
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
    val syncState by runtime.sourceSyncState.collectAsState()
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()

    var section by remember { mutableStateOf(TvOwnPlaySection.HOME) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var requestedVodMovieId by remember { mutableStateOf<String?>(null) }
    var requestedSeriesId by remember { mutableStateOf<String?>(null) }
    var vodFullscreen by remember { mutableStateOf(false) }
    var seriesFullscreen by remember { mutableStateOf(false) }
    var libraryFullscreen by remember { mutableStateOf(false) }
    val liveTransitionGate = remember { LivePlaybackTransitionGate() }

    val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId }

    fun stopLive(clearPresentation: () -> Unit = {}) {
        LivePlaybackSurfaceTeardown.stopAfterDetaching(
            detachCurrentSurface = { PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput) },
            stopPlayback = runtime.playbackController::stop,
            clearPresentation = {
                activeSelection = null
                fullscreenSelection = null
                clearPresentation()
            },
        )
    }

    fun openSection(target: TvOwnPlaySection) {
        if (target != TvOwnPlaySection.LIVE && (activeSelection != null || fullscreenSelection != null)) {
            stopLive()
        }
        section = target
    }

    fun openLiveFullscreen(selection: LivePlaybackSelection) {
        liveTransitionGate.requestHandoff(
            target = LivePlaybackTransitionTarget.fullscreen(selection),
            detachCurrentSurface = { PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput) },
            stopPlayback = runtime.playbackController::stop,
            switchPresentation = {
                activeSelection = selection
                fullscreenSelection = selection
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    fun returnLiveToBrowse(selection: LivePlaybackSelection) {
        liveTransitionGate.requestHandoff(
            target = LivePlaybackTransitionTarget.preview(selection),
            detachCurrentSurface = { PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput) },
            stopPlayback = runtime.playbackController::stop,
            switchPresentation = {
                section = TvOwnPlaySection.LIVE
                activeSourceId = selection.request.sourceId
                activeSelection = selection
                fullscreenSelection = null
            },
            startPlayback = { runtime.playbackController.start(selection.request) },
        )
    }

    LaunchedEffect(summaries) {
        val ids = summaries.map { it.sourceId }.toSet()
        activeSourceId = when {
            activeSourceId in ids -> activeSourceId
            summaries.isNotEmpty() -> summaries.first().sourceId
            else -> null
        }
        val selectionSource = activeSelection?.request?.sourceId
        if (selectionSource != null && selectionSource !in ids) stopLive()
    }

    val previewActive =
        section == TvOwnPlaySection.LIVE && activeSelection != null && fullscreenSelection == null
    val playbackSurfaceActive =
        previewActive || fullscreenSelection != null || vodFullscreen || seriesFullscreen || libraryFullscreen

    LaunchedEffect(playbackSurfaceActive) { onPlaybackSurfaceActiveChanged(playbackSurfaceActive) }
    LaunchedEffect(previewActive) { onLivePreviewActiveChanged(false) }
    LaunchedEffect(fullscreenSelection != null) { onPlaybackFullscreenChanged(fullscreenSelection != null) }

    val observedTarget = fullscreenSelection?.let(LivePlaybackTransitionTarget::fullscreen)
        ?: if (previewActive) activeSelection?.let(LivePlaybackTransitionTarget::preview) else null
    SideEffect { liveTransitionGate.reconcileObserved(observedTarget) }

    val openedFullscreen = fullscreenSelection
    if (openedFullscreen != null) {
        TvLiveFullscreenPlayer(
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
                (fullscreenSelection ?: openedFullscreen).navigate(direction)?.let { target ->
                    activeSelection = target
                    fullscreenSelection = target
                    runtime.playbackController.start(target.request)
                }
            },
            onReturnToChannels = { returnLiveToBrowse(fullscreenSelection ?: openedFullscreen) },
        )
        return
    }

    val primaryBackDestination = tvPrimarySectionBackDestination(section)
    BackHandler(enabled = primaryBackDestination != null) {
        openSection(requireNotNull(primaryBackDestination))
    }

    when (section) {
        TvOwnPlaySection.HOME -> TvHomeScreen(
            onLive = { openSection(TvOwnPlaySection.LIVE) },
            onLibrary = { openSection(TvOwnPlaySection.LIBRARY) },
            onSettings = { openSection(TvOwnPlaySection.SETTINGS) },
        )

        TvOwnPlaySection.LIVE -> {
            val sourceId = activeSourceId
            if (sourceId == null) {
                TvNoSourceScreen(onOpenSettings = { openSection(TvOwnPlaySection.SETTINGS) })
            } else {
                TvLiveRoute(
                    runtime = runtime,
                    sourceId = sourceId,
                    activeSelection = activeSelection,
                    playbackState = playbackState,
                    videoOutput = runtime.playbackVideoOutput,
                    syncState = syncState,
                    onPlay = runtime.playbackController::play,
                    onPause = runtime.playbackController::pause,
                    onRetry = runtime.playbackController::retry,
                    onOpenSettings = { openSection(TvOwnPlaySection.SETTINGS) },
                    onPreviewRequested = { selection ->
                        activeSelection = selection
                        runtime.playbackController.start(selection.request)
                    },
                    onPreviewClosed = { stopLive() },
                    onOpenFullscreen = ::openLiveFullscreen,
                )
            }
        }

        TvOwnPlaySection.LIBRARY -> TvLibraryRoute(
            runtime = runtime,
            sourceId = activeSourceId,
            onOpenMovieDetails = { sourceId, movieId ->
                activeSourceId = sourceId
                requestedVodMovieId = movieId
                section = TvOwnPlaySection.MOVIE_DETAILS
            },
            onOpenSeriesDetails = { sourceId, seriesId ->
                activeSourceId = sourceId
                requestedSeriesId = seriesId
                section = TvOwnPlaySection.SERIES_DETAILS
            },
            onFullscreenStateChanged = { fullscreen ->
                libraryFullscreen = fullscreen
                onPlaybackFullscreenChanged(fullscreen)
            },
        )

        TvOwnPlaySection.MOVIE_DETAILS -> TvLibraryMovieDetailRoute(
            runtime = runtime,
            sourceId = activeSourceId,
            sourceKind = activeSummary?.sourceKind,
            movieId = requestedVodMovieId,
            onMovieConsumed = { requestedVodMovieId = null },
            onBackToLibrary = { section = TvOwnPlaySection.LIBRARY },
            onOpenSettings = { section = TvOwnPlaySection.SETTINGS },
            onFullscreenStateChanged = { fullscreen ->
                vodFullscreen = fullscreen
                onPlaybackFullscreenChanged(fullscreen)
            },
        )

        TvOwnPlaySection.SERIES_DETAILS -> TvLibrarySeriesDetailRoute(
            runtime = runtime,
            sourceId = activeSourceId,
            sourceKind = activeSummary?.sourceKind,
            seriesId = requestedSeriesId,
            onSeriesConsumed = { requestedSeriesId = null },
            onBackToLibrary = { section = TvOwnPlaySection.LIBRARY },
            onOpenSettings = { section = TvOwnPlaySection.SETTINGS },
            onFullscreenStateChanged = { fullscreen ->
                seriesFullscreen = fullscreen
                onPlaybackFullscreenChanged(fullscreen)
            },
        )

        TvOwnPlaySection.SETTINGS -> SettingsScreen(
            runtime = runtime,
            summaries = summaries,
            syncState = syncState,
            activeSourceName = activeSummary?.name,
            hasActivePlayback = activeSelection != null || vodFullscreen || seriesFullscreen || libraryFullscreen,
            onOpenLive = { section = TvOwnPlaySection.LIVE },
            onOpenSourceInLive = { sourceId ->
                if (sourceId != activeSourceId && activeSelection != null) stopLive()
                activeSourceId = sourceId
                section = TvOwnPlaySection.LIVE
            },
            onStopPlayback = {
                if (activeSelection != null || fullscreenSelection != null) stopLive()
                else runtime.playbackController.stop()
            },
        )
    }
}

@Composable
private fun TvHomeScreen(
    onLive: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "OwnPlay",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Choose where you want to go",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TvHomeCard(
                title = "Live",
                subtitle = "Channels and program guide",
                icon = Icons.Filled.LiveTv,
                onClick = onLive,
                modifier = Modifier.weight(1f),
            )
            TvHomeCard(
                title = "Library",
                subtitle = "Offline, Movies and Series",
                icon = Icons.Filled.DownloadDone,
                onClick = onLibrary,
                modifier = Modifier.weight(1f),
            )
            TvHomeCard(
                title = "Settings",
                subtitle = "Sources and preferences",
                icon = Icons.Filled.Settings,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TvHomeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (focused) 8.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TvNoSourceScreen(onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No playlist configured", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Add a playlist from Settings to start watching Live TV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }
    }
}

@Composable
private fun TvLiveFullscreenPlayer(
    selection: LivePlaybackSelection,
    state: app.ownplay.player.playback.PlaybackState,
    trackState: app.ownplay.player.playback.PlaybackTrackState,
    videoOutput: app.ownplay.player.playback.PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onAudioSelection: (app.ownplay.player.playback.PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (app.ownplay.player.playback.PlaybackSubtitleSelection) -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onReturnToChannels: () -> Unit,
) {
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
        onRemoteChannelNavigate = onNavigate,
    )
}
