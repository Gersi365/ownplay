package app.ownplay.player.ui

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun LivePreviewPanel(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onOpenFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val controls = PlaybackPresentationPolicy.controlsFor(state)
    var controlsVisible by remember(selection.request.channelId) { mutableStateOf(true) }
    var interactionToken by remember(selection.request.channelId) { mutableStateOf(0) }
    val fullscreenFocusRequester = remember(selection.request.channelId) { FocusRequester() }
    val closeFocusRequester = remember(selection.request.channelId) { FocusRequester() }

    fun revealControls() {
        controlsVisible = true
        interactionToken += 1
    }

    LaunchedEffect(selection.request.channelId, state, interactionToken, isTelevision) {
        if (isTelevision) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (state is PlaybackState.Playing) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    LaunchedEffect(selection.request.channelId, state, isTelevision) {
        if (!isTelevision) return@LaunchedEffect
        if (state is PlaybackState.Failed) {
            closeFocusRequester.requestFocus()
        } else {
            fullscreenFocusRequester.requestFocus()
        }
    }

    val previewInteractionModifier = if (isTelevision) {
        Modifier
    } else {
        Modifier.clickable(onClick = ::revealControls)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .then(previewInteractionModifier),
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        videoOutput.bind(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { view -> videoOutput.unbind(view) },
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
            ) {
                Text(
                    text = "LIVE",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (controls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (state is PlaybackState.Failed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = playbackStatusLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        if (controls.canRetry) {
                            TextButton(
                                onClick = {
                                    revealControls()
                                    onRetry()
                                },
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible || state !is PlaybackState.Playing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(150)) +
                    slideInVertically(
                        animationSpec = tween(170),
                        initialOffsetY = { fullHeight -> fullHeight / 5 },
                    ),
                exit = fadeOut(tween(120)) +
                    slideOutVertically(
                        animationSpec = tween(140),
                        targetOffsetY = { fullHeight -> fullHeight / 6 },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(
                            text = selection.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playbackStatusLabel(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewControlSlot {
                            IconButton(
                                onClick = {
                                    revealControls()
                                    onNavigate(PlaybackNavigationDirection.PREVIOUS)
                                },
                                enabled = selection.request.navigationTarget(
                                    PlaybackNavigationDirection.PREVIOUS,
                                ) != null,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous channel",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        PreviewControlSlot {
                            FilledIconButton(
                                onClick = if (controls.canPause) {
                                    {
                                        revealControls()
                                        onPause()
                                    }
                                } else {
                                    {
                                        revealControls()
                                        onPlay()
                                    }
                                },
                                enabled = controls.canPause || controls.canPlay,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = if (controls.canPause) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = if (controls.canPause) "Pause" else "Play",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        PreviewControlSlot {
                            IconButton(
                                onClick = {
                                    revealControls()
                                    onNavigate(PlaybackNavigationDirection.NEXT)
                                },
                                enabled = selection.request.navigationTarget(
                                    PlaybackNavigationDirection.NEXT,
                                ) != null,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "Next channel",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        PreviewControlSlot {
                            IconButton(
                                onClick = onOpenFullscreen,
                                modifier = Modifier
                                    .size(38.dp)
                                    .focusRequester(fullscreenFocusRequester),
                            ) {
                                Icon(
                                    Icons.Filled.Fullscreen,
                                    contentDescription = "Open full player",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        PreviewControlSlot {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(38.dp)
                                    .focusRequester(closeFocusRequester),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close preview",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PreviewControlSlot(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
