package app.ownplay.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackAudioSelection
import app.ownplay.player.playback.PlaybackControlAvailability
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackResizeMode
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackSubtitleSelection
import app.ownplay.player.playback.PlaybackTrackOption
import app.ownplay.player.playback.PlaybackTrackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.ui.live.LiveBrowseScreen
import kotlinx.coroutines.delay

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
            onReturnToChannels = {
                route = OwnPlayRoute.Live(current.selection.request.sourceId)
            },
        )
    }
}

@Composable
private fun SourcePickerScreen(
    sources: List<PlaylistSourceEntity>,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    onSourceSelected: (String) -> Unit,
    onResumePlayback: (LivePlaybackSelection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            Text(
                text = "OwnPlay",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your media. Your way.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (activeSelection != null && playbackState !is PlaybackState.Idle) {
                ActivePlaybackBar(
                    selection = activeSelection,
                    state = playbackState,
                    onOpen = { onResumePlayback(activeSelection) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (sources.isEmpty()) {
                Text(
                    text = "No playlists yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OwnPlay plays and organizes media sources you add. " +
                        "It does not provide channels, subscriptions, or IPTV services.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = sources,
                        key = PlaylistSourceEntity::sourceId,
                    ) { source ->
                        SourceRow(
                            source = source,
                            onOpen = { onSourceSelected(source.sourceId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: PlaylistSourceEntity,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = source.enabled, onClick = onOpen),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (source.enabled) "Ready" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (source.enabled) {
                TextButton(onClick = onOpen) {
                    Text("Live")
                }
            }
        }
    }
}

@Composable
private fun LiveRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    onBackToSources: () -> Unit,
    onPlaybackRequested: (LivePlaybackSelection) -> Unit,
    onResumePlayback: (LivePlaybackSelection) -> Unit,
) {
    val browseSession = remember(sourceId) { LiveBrowseSession() }
    val browseFlow = remember(sourceId) {
        browseSession.observe(runtime.observeLiveCatalog(sourceId))
    }
    val browseState by browseFlow.collectAsState(initial = LiveBrowseState())
    var editState by remember(sourceId) { mutableStateOf(ChannelEditState()) }

    LaunchedEffect(browseState.channels, editState.isEditing) {
        editState = ChannelEditReducer.retainAvailable(
            state = editState,
            availableChannelIds = browseState.channels.map { channel -> channel.channelId },
        )
    }

    BackHandler(onBack = onBackToSources)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBackToSources) {
                Text("Sources")
            }
            Spacer(modifier = Modifier.weight(1f))
            val resumable = activeSelection?.takeIf { selection ->
                selection.request.sourceId == sourceId && playbackState !is PlaybackState.Idle
            }
            if (resumable != null) {
                TextButton(onClick = { onResumePlayback(resumable) }) {
                    Text("Now playing")
                }
            }
        }
        HorizontalDivider()

        LiveBrowseScreen(
            state = browseState,
            onSearchChange = browseSession::updateSearch,
            onCategorySelected = browseSession::selectCategory,
            onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
            onOrderChanged = browseSession::setOrder,
            onCustomGroupSelected = browseSession::selectCustomGroup,
            onHiddenOnlyChanged = browseSession::setHiddenOnly,
            editState = editState,
            onEditModeChanged = { editing ->
                editState = if (editing) {
                    ChannelEditReducer.enter(editState)
                } else {
                    ChannelEditReducer.exit(editState)
                }
            },
            onChannelSelectionToggle = { channelId ->
                editState = ChannelEditReducer.toggleSelection(editState, channelId)
            },
            onSelectVisible = {
                editState = ChannelEditReducer.selectVisible(
                    state = editState,
                    visibleChannelIds = browseState.channels.map { channel -> channel.channelId },
                )
            },
            onClearSelection = {
                editState = ChannelEditReducer.clearSelection(editState)
            },
            onChannelSelected = { channelId ->
                val channel = browseState.channels.firstOrNull { item -> item.channelId == channelId }
                    ?: return@LiveBrowseScreen
                when (
                    val action = LiveChannelSelectionRouter.route(
                        channel = channel,
                        isEditing = editState.isEditing,
                    )
                ) {
                    is LiveChannelSelectionAction.ToggleEditSelection -> {
                        editState = ChannelEditReducer.toggleSelection(
                            state = editState,
                            channelId = action.channelId,
                        )
                    }
                    is LiveChannelSelectionAction.StartPlayback -> {
                        onPlaybackRequested(action.selection)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlaybackScreen(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    trackState: PlaybackTrackState,
    videoOutput: PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onAudioSelection: (PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (PlaybackSubtitleSelection) -> Unit,
    onReturnToChannels: () -> Unit,
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(PlaybackResizeMode.FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    val controls = PlaybackPresentationPolicy.controlsFor(state)

    FullscreenSystemBarsEffect(enabled = isFullscreen)

    LaunchedEffect(state, controlsVisible, showTracks) {
        if (state is PlaybackState.Playing && controlsVisible && !showTracks) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    BackHandler {
        when {
            showTracks -> showTracks = false
            isFullscreen -> {
                isFullscreen = false
                controlsVisible = true
            }
            else -> onReturnToChannels()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerVideoSurface(
                videoOutput = videoOutput,
                resizeMode = resizeMode,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (showTracks) {
                            showTracks = false
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
            ) {}

            if (controls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (state is PlaybackState.Failed) {
                PlaybackFailureOverlay(
                    state = state,
                    canRetry = controls.canRetry,
                    onRetry = {
                        controlsVisible = true
                        onRetry()
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (controlsVisible || state !is PlaybackState.Playing) {
                PlaybackControlsOverlay(
                    selection = selection,
                    state = state,
                    controls = controls,
                    resizeMode = resizeMode,
                    isFullscreen = isFullscreen,
                    onPlay = {
                        controlsVisible = true
                        onPlay()
                    },
                    onPause = {
                        controlsVisible = true
                        onPause()
                    },
                    onResizeModeChanged = {
                        resizeMode = resizeMode.next()
                        controlsVisible = true
                    },
                    onFullscreenChanged = {
                        isFullscreen = !isFullscreen
                        controlsVisible = true
                    },
                    onTracksRequested = {
                        showTracks = true
                        controlsVisible = true
                    },
                    onReturnToChannels = onReturnToChannels,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }

            if (showTracks) {
                PlaybackTracksPanel(
                    state = trackState,
                    onAudioSelection = onAudioSelection,
                    onSubtitleSelection = onSubtitleSelection,
                    onClose = { showTracks = false },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerVideoSurface(
    videoOutput: PlaybackVideoOutput,
    resizeMode: PlaybackResizeMode,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShutterBackgroundColor(AndroidColor.BLACK)
                videoOutput.bind(this)
            }
        },
        modifier = modifier,
        update = { view ->
            view.useController = false
            view.resizeMode = resizeMode.toMedia3ResizeMode()
        },
        onRelease = { view ->
            videoOutput.unbind(view)
        },
    )
}

@Composable
private fun PlaybackFailureOverlay(
    state: PlaybackState.Failed,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = playbackStatusLabel(state),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlsOverlay(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    controls: PlaybackControlAvailability,
    resizeMode: PlaybackResizeMode,
    isFullscreen: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResizeModeChanged: () -> Unit,
    onFullscreenChanged: () -> Unit,
    onTracksRequested: () -> Unit,
    onReturnToChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = selection.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = playbackStatusLabel(state),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReturnToChannels) {
                Text("Channels")
            }
            if (controls.canPlay) {
                TextButton(onClick = onPlay) {
                    Text("Play")
                }
            }
            if (controls.canPause) {
                TextButton(onClick = onPause) {
                    Text("Pause")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onTracksRequested,
                enabled = state is PlaybackState.Playing || state is PlaybackState.Paused,
            ) {
                Text("Tracks")
            }
            TextButton(onClick = onResizeModeChanged) {
                Text(resizeModeLabel(resizeMode))
            }
            TextButton(onClick = onFullscreenChanged) {
                Text(if (isFullscreen) "Exit fullscreen" else "Fullscreen")
            }
        }
    }
}

@Composable
private fun PlaybackTracksPanel(
    state: PlaybackTrackState,
    onAudioSelection: (PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (PlaybackSubtitleSelection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }

            Text(
                text = "Audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            TrackChoiceButton(
                label = "Default",
                selected = state.audioSelection == PlaybackAudioSelection.Default,
                onClick = { onAudioSelection(PlaybackAudioSelection.Default) },
            )
            if (state.audioTracks.isEmpty()) {
                TrackEmptyLabel("No alternate audio tracks")
            } else {
                state.audioTracks.forEach { option ->
                    val selected = (state.audioSelection as? PlaybackAudioSelection.Specific)
                        ?.trackId == option.id
                    TrackOptionButton(
                        option = option,
                        selected = selected,
                        onClick = {
                            onAudioSelection(PlaybackAudioSelection.Specific(option.id))
                        },
                    )
                }
            }

            HorizontalDivider()
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            TrackChoiceButton(
                label = "Default",
                selected = state.subtitleSelection == PlaybackSubtitleSelection.Default,
                onClick = { onSubtitleSelection(PlaybackSubtitleSelection.Default) },
            )
            TrackChoiceButton(
                label = "Off",
                selected = state.subtitleSelection == PlaybackSubtitleSelection.Off,
                onClick = { onSubtitleSelection(PlaybackSubtitleSelection.Off) },
            )
            if (state.subtitleTracks.isEmpty()) {
                TrackEmptyLabel("No subtitles")
            } else {
                state.subtitleTracks.forEach { option ->
                    val selected = (state.subtitleSelection as? PlaybackSubtitleSelection.Specific)
                        ?.trackId == option.id
                    TrackOptionButton(
                        option = option,
                        selected = selected,
                        onClick = {
                            onSubtitleSelection(PlaybackSubtitleSelection.Specific(option.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackOptionButton(
    option: PlaybackTrackOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val activeSuffix = if (option.selectedByPlayer) " · active" else ""
    TrackChoiceButton(
        label = option.label + activeSuffix,
        selected = selected,
        enabled = option.supported,
        onClick = onClick,
    )
}

@Composable
private fun TrackChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackEmptyLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun FullscreenSystemBarsEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        val controller = activity?.let { host ->
            WindowCompat.getInsetsController(host.window, host.window.decorView)
        }

        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (enabled) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
private fun PlaybackResizeMode.toMedia3ResizeMode(): Int = when (this) {
    PlaybackResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PlaybackResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    PlaybackResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

private fun resizeModeLabel(mode: PlaybackResizeMode): String = when (mode) {
    PlaybackResizeMode.FIT -> "Fit"
    PlaybackResizeMode.FILL -> "Fill"
    PlaybackResizeMode.ZOOM -> "Zoom"
}

@Composable
private fun ActivePlaybackBar(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = playbackStatusLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

private fun playbackStatusLabel(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "Idle"
    is PlaybackState.Loading -> "Starting playback…"
    is PlaybackState.Playing -> "Playing"
    is PlaybackState.Paused -> "Paused"
    is PlaybackState.Failed -> when (state.failure.category) {
        PlaybackFailureCategory.NETWORK_UNAVAILABLE -> "Network unavailable"
        PlaybackFailureCategory.TIMEOUT -> "Playback timed out"
        PlaybackFailureCategory.AUTHENTICATION_FAILURE -> "Authentication failed"
        PlaybackFailureCategory.STREAM_UNAVAILABLE -> "Stream unavailable"
        PlaybackFailureCategory.UNSUPPORTED_MEDIA -> "Unsupported media"
        PlaybackFailureCategory.UNKNOWN -> "Playback failed"
    }
}
