package app.ownplay.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackAudioSelection
import app.ownplay.player.playback.PlaybackControlAvailability
import app.ownplay.player.playback.PlaybackDiagnostics
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackResizeMode
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackSubtitleSelection
import app.ownplay.player.playback.PlaybackTrackOption
import app.ownplay.player.playback.PlaybackTrackState
import app.ownplay.player.playback.PlaybackVideoOutput
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackScreen(
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
    onFullscreenStateChanged: (Boolean) -> Unit,
    epgContent: @Composable () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val controlsFocusRequester = remember { FocusRequester() }
    val wakeFocusRequester = remember { FocusRequester() }
    val tracksFocusRequester = remember { FocusRequester() }
    val diagnosticsFocusRequester = remember { FocusRequester() }
    val failureFocusRequester = remember { FocusRequester() }
    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(PlaybackResizeMode.FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var controlsInteractionToken by remember { mutableStateOf(0) }
    val controls = PlaybackPresentationPolicy.controlsFor(state)

    fun revealControls() {
        controlsVisible = true
        if (isTelevision) {
            controlsInteractionToken += 1
        }
    }

    FullscreenSystemBarsEffect(enabled = isFullscreen)

    LaunchedEffect(isFullscreen) {
        onFullscreenStateChanged(isFullscreen)
    }
    DisposableEffect(onFullscreenStateChanged) {
        onDispose { onFullscreenStateChanged(false) }
    }

    LaunchedEffect(state) {
        if (state is PlaybackState.Failed) {
            showTracks = false
            showDiagnostics = false
            controlsVisible = true
        }
    }

    LaunchedEffect(state, controlsVisible, showTracks, showDiagnostics, controlsInteractionToken) {
        if (
            playbackControlsShouldAutoHide(
                state = state,
                controlsVisible = controlsVisible,
                transientPanelVisible = showTracks || showDiagnostics,
            )
        ) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(
        isTelevision,
        state,
        controlsVisible,
        showTracks,
        showDiagnostics,
        selection.request.channelId,
    ) {
        if (!isTelevision) return@LaunchedEffect
        when {
            state is PlaybackState.Failed -> {
                withFrameNanos { }
                failureFocusRequester.requestFocus()
            }
            showTracks -> tracksFocusRequester.requestFocus()
            showDiagnostics -> diagnosticsFocusRequester.requestFocus()
            !controlsVisible && state is PlaybackState.Playing -> wakeFocusRequester.requestFocus()
            else -> controlsFocusRequester.requestFocus()
        }
    }

    BackHandler {
        when {
            showDiagnostics -> showDiagnostics = false
            showTracks -> showTracks = false
            isFullscreen -> {
                isFullscreen = false
                revealControls()
            }
            else -> onReturnToChannels()
        }
    }

    val remoteWakeModifier = if (
        isTelevision &&
        !controlsVisible &&
        state is PlaybackState.Playing
    ) {
        Modifier
            .focusRequester(wakeFocusRequester)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.isRemoteNavigationKeyDown()) {
                    revealControls()
                    true
                } else {
                    false
                }
            }
            .focusable()
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (
                        isTelevision &&
                        state !is PlaybackState.Failed &&
                        controlsVisible &&
                        !showTracks &&
                        !showDiagnostics &&
                        event.nativeKeyEvent.isRemoteNavigationKeyDown()
                    ) {
                        controlsInteractionToken += 1
                    }
                    false
                },
        ) {
            PlayerVideoSurface(
                videoOutput = videoOutput,
                resizeMode = resizeMode,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state, controlsVisible, showTracks, showDiagnostics) {
                        detectTapGestures {
                            when {
                                state is PlaybackState.Failed -> Unit
                                showDiagnostics -> showDiagnostics = false
                                showTracks -> showTracks = false
                                controlsVisible -> controlsVisible = false
                                else -> revealControls()
                            }
                        }
                    }
                    .then(remoteWakeModifier),
            ) {}

            if (controls.showLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(0.72f),
            ) {
                epgContent()
            }

            if (
                state !is PlaybackState.Failed &&
                (controlsVisible || state !is PlaybackState.Playing)
            ) {
                PlaybackControlsOverlay(
                    selection = selection,
                    state = state,
                    controls = controls,
                    resizeMode = resizeMode,
                    isFullscreen = isFullscreen,
                    controlsFocusRequester = controlsFocusRequester,
                    onPlay = {
                        revealControls()
                        onPlay()
                    },
                    onPause = {
                        revealControls()
                        onPause()
                    },
                    onNavigate = { direction ->
                        showTracks = false
                        showDiagnostics = false
                        revealControls()
                        onNavigate(direction)
                    },
                    onResizeModeChanged = {
                        resizeMode = resizeMode.next()
                        revealControls()
                    },
                    onFullscreenChanged = {
                        isFullscreen = !isFullscreen
                        revealControls()
                    },
                    onTracksRequested = {
                        showDiagnostics = false
                        showTracks = true
                        revealControls()
                    },
                    onDiagnosticsRequested = {
                        showTracks = false
                        showDiagnostics = true
                        revealControls()
                    },
                    onReturnToChannels = onReturnToChannels,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }

            if (state is PlaybackState.Failed) {
                PlaybackFailureOverlay(
                    state = state,
                    canRetry = controls.canRetry,
                    onRetry = {
                        revealControls()
                        onRetry()
                    },
                    onReturnToChannels = onReturnToChannels,
                    entryFocusRequester = failureFocusRequester,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (showTracks) {
                PlaybackTracksPanel(
                    state = trackState,
                    onAudioSelection = onAudioSelection,
                    onSubtitleSelection = onSubtitleSelection,
                    onClose = {
                        showTracks = false
                        revealControls()
                    },
                    entryFocusRequester = tracksFocusRequester,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.86f)
                        .padding(16.dp),
                )
            }

            if (showDiagnostics) {
                PlaybackDiagnosticsPanel(
                    diagnostics = trackState.diagnostics,
                    onClose = {
                        showDiagnostics = false
                        revealControls()
                    },
                    closeFocusRequester = diagnosticsFocusRequester,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f)
                        .padding(16.dp),
                )
            }
        }
    }
}

internal fun playbackControlsShouldAutoHide(
    state: PlaybackState,
    controlsVisible: Boolean,
    transientPanelVisible: Boolean,
): Boolean =
    state is PlaybackState.Playing && controlsVisible && !transientPanelVisible

internal enum class PlaybackFailureEntryAction {
    RETRY,
    RETURN_TO_CHANNELS,
}

internal fun playbackFailureEntryAction(canRetry: Boolean): PlaybackFailureEntryAction =
    if (canRetry) PlaybackFailureEntryAction.RETRY else PlaybackFailureEntryAction.RETURN_TO_CHANNELS

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
        onRelease = { view -> videoOutput.unbind(view) },
    )
}

@Composable
private fun PlaybackFailureOverlay(
    state: PlaybackState.Failed,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onReturnToChannels: () -> Unit,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val entryAction = playbackFailureEntryAction(canRetry)

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) {
                    TextButton(
                        onClick = onRetry,
                        modifier = if (entryAction == PlaybackFailureEntryAction.RETRY) {
                            Modifier.focusRequester(entryFocusRequester)
                        } else {
                            Modifier
                        },
                    ) { Text("Retry") }
                }
                TextButton(
                    onClick = onReturnToChannels,
                    modifier = if (entryAction == PlaybackFailureEntryAction.RETURN_TO_CHANNELS) {
                        Modifier.focusRequester(entryFocusRequester)
                    } else {
                        Modifier
                    },
                ) { Text("Back to channels") }
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
    controlsFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onResizeModeChanged: () -> Unit,
    onFullscreenChanged: () -> Unit,
    onTracksRequested: () -> Unit,
    onDiagnosticsRequested: () -> Unit,
    onReturnToChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.78f))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playbackStatusLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDiagnosticsRequested) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Playback diagnostics",
                    tint = Color.White,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackControlSlot {
                IconButton(
                    modifier = if (controls.canPause || controls.canPlay) {
                        Modifier
                    } else {
                        Modifier.focusRequester(controlsFocusRequester)
                    },
                    onClick = onReturnToChannels,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to channels",
                        tint = Color.White,
                    )
                }
            }
            PlaybackControlSlot {
                IconButton(
                    onClick = { onNavigate(PlaybackNavigationDirection.PREVIOUS) },
                    enabled = selection.request.navigationTarget(
                        PlaybackNavigationDirection.PREVIOUS,
                    ) != null,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous channel",
                        tint = Color.White,
                    )
                }
            }
            PlaybackControlSlot {
                FilledIconButton(
                    modifier = if (controls.canPause || controls.canPlay) {
                        Modifier.focusRequester(controlsFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = if (controls.canPause) onPause else onPlay,
                    enabled = controls.canPause || controls.canPlay,
                ) {
                    Icon(
                        imageVector = if (controls.canPause) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (controls.canPause) "Pause" else "Play",
                    )
                }
            }
            PlaybackControlSlot {
                IconButton(
                    onClick = { onNavigate(PlaybackNavigationDirection.NEXT) },
                    enabled = selection.request.navigationTarget(
                        PlaybackNavigationDirection.NEXT,
                    ) != null,
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next channel",
                        tint = Color.White,
                    )
                }
            }
            PlaybackControlSlot {
                IconButton(onClick = onFullscreenChanged) {
                    Icon(
                        imageVector = if (isFullscreen) {
                            Icons.Filled.FullscreenExit
                        } else {
                            Icons.Filled.Fullscreen
                        },
                        contentDescription = if (isFullscreen) {
                            "Exit fullscreen"
                        } else {
                            "Enter fullscreen"
                        },
                        tint = Color.White,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onTracksRequested,
                enabled = state is PlaybackState.Playing || state is PlaybackState.Paused,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tracks")
            }
            OutlinedButton(
                onClick = onResizeModeChanged,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.AspectRatio, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(resizeModeLabel(resizeMode))
            }
        }
    }
}

@Composable
private fun RowScope.PlaybackControlSlot(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PlaybackTracksPanel(
    state: PlaybackTrackState,
    onAudioSelection: (PlaybackAudioSelection) -> Unit,
    onSubtitleSelection: (PlaybackSubtitleSelection) -> Unit,
    onClose: () -> Unit,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val entryTarget = playbackTracksEntryTarget(
        audioSelection = state.audioSelection,
        supportedAudioTrackIds = state.audioTracks
            .filter(PlaybackTrackOption::supported)
            .map(PlaybackTrackOption::id)
            .toSet(),
    )

    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
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
                "Audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            TrackChoiceButton(
                label = "Default",
                selected = state.audioSelection == PlaybackAudioSelection.Default,
                focusRequester = entryFocusRequester.takeIf {
                    entryTarget == PlaybackTracksEntryTarget.DefaultAudio
                },
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
                        focusRequester = entryFocusRequester.takeIf {
                            entryTarget == PlaybackTracksEntryTarget.SpecificAudio(option.id)
                        },
                        onClick = { onAudioSelection(PlaybackAudioSelection.Specific(option.id)) },
                    )
                }
            }

            HorizontalDivider()
            Text(
                "Subtitles",
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
                        onClick = { onSubtitleSelection(PlaybackSubtitleSelection.Specific(option.id)) },
                    )
                }
            }
        }
    }
}

internal sealed interface PlaybackTracksEntryTarget {
    data object DefaultAudio : PlaybackTracksEntryTarget
    data class SpecificAudio(val trackId: String) : PlaybackTracksEntryTarget
}

internal fun playbackTracksEntryTarget(
    audioSelection: PlaybackAudioSelection,
    supportedAudioTrackIds: Set<String>,
): PlaybackTracksEntryTarget = when (audioSelection) {
    PlaybackAudioSelection.Default -> PlaybackTracksEntryTarget.DefaultAudio
    is PlaybackAudioSelection.Specific -> {
        if (audioSelection.trackId in supportedAudioTrackIds) {
            PlaybackTracksEntryTarget.SpecificAudio(audioSelection.trackId)
        } else {
            PlaybackTracksEntryTarget.DefaultAudio
        }
    }
}

@Composable
private fun PlaybackDiagnosticsPanel(
    diagnostics: PlaybackDiagnostics,
    onClose: () -> Unit,
    closeFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playback diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    modifier = Modifier.focusRequester(closeFocusRequester),
                    onClick = onClose,
                ) {
                    Text("Close")
                }
            }

            Text(
                text = "Technical media information only. Stream URLs and credentials are never shown here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticRow("Decoder policy", diagnostics.rendererPolicy)

            HorizontalDivider()
            Text("Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            DiagnosticRow("Format", diagnostics.audioMimeType ?: "Not reported")
            DiagnosticRow("Codec", diagnostics.audioCodecs ?: "Not reported")
            DiagnosticRow("Decoder", diagnostics.audioDecoder ?: "Pending / not reported")
            DiagnosticRow("FFmpeg", if (diagnostics.usingFfmpegAudio) "Active fallback" else "Not active")
            DiagnosticRow(
                "Output",
                listOfNotNull(
                    diagnostics.audioChannelCount?.let { "$it ch" },
                    diagnostics.audioSampleRate?.let { "$it Hz" },
                ).joinToString(" · ").ifBlank { "Not reported" },
            )
            DiagnosticRow("Language", diagnostics.audioLanguage ?: "Not reported")

            HorizontalDivider()
            Text("Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            DiagnosticRow("Format", diagnostics.videoMimeType ?: "Not reported")
            DiagnosticRow("Codec", diagnostics.videoCodecs ?: "Not reported")
            DiagnosticRow("Decoder", diagnostics.videoDecoder ?: "Pending / not reported")
            DiagnosticRow(
                "Resolution",
                if (diagnostics.videoWidth != null && diagnostics.videoHeight != null) {
                    "${diagnostics.videoWidth} × ${diagnostics.videoHeight}"
                } else {
                    "Not reported"
                },
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TrackOptionButton(
    option: PlaybackTrackOption,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val activeSuffix = if (option.selectedByPlayer) " · active" else ""
    TrackChoiceButton(
        label = option.label + activeSuffix,
        selected = selected,
        enabled = option.supported,
        focusRequester = focusRequester,
        onClick = onClick,
    )
}

@Composable
private fun TrackChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            ),
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
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun KeyEvent.isRemoteNavigationKeyDown(): Boolean =
    action == KeyEvent.ACTION_DOWN &&
        keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )

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
