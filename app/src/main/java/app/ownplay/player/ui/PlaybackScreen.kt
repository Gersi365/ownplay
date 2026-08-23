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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(PlaybackResizeMode.FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    val controls = PlaybackPresentationPolicy.controlsFor(state)

    FullscreenSystemBarsEffect(enabled = isFullscreen)

    LaunchedEffect(isFullscreen) {
        onFullscreenStateChanged(isFullscreen)
    }
    DisposableEffect(onFullscreenStateChanged) {
        onDispose { onFullscreenStateChanged(false) }
    }

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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    onNavigate = { direction ->
                        showTracks = false
                        controlsVisible = true
                        onNavigate(direction)
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
                        .fillMaxHeight(0.86f)
                        .padding(16.dp),
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
        onRelease = { view -> videoOutput.unbind(view) },
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
                TextButton(onClick = onRetry) { Text("Retry") }
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
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onResizeModeChanged: () -> Unit,
    onFullscreenChanged: () -> Unit,
    onTracksRequested: () -> Unit,
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
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackControlSlot {
                IconButton(onClick = onReturnToChannels) {
                    Icon(
                        Icons.Filled.ArrowBack,
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
    modifier: Modifier = Modifier,
) {
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
                TextButton(onClick = onClose) { Text("Close") }
            }

            Text(
                "Audio",
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
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
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
