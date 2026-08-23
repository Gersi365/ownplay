package app.ownplay.player.ui

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val controls = PlaybackPresentationPolicy.controlsFor(state)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable(onClick = onOpenFullscreen),
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
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (controls.showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp),
                    )
                }

                if (state is PlaybackState.Failed) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = playbackStatusLabel(state),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (controls.canRetry) {
                                TextButton(onClick = onRetry) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selection.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playbackStatusLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewControlSlot {
                        IconButton(
                            onClick = { onNavigate(PlaybackNavigationDirection.PREVIOUS) },
                            enabled = selection.request.navigationTarget(
                                PlaybackNavigationDirection.PREVIOUS,
                            ) != null,
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous channel")
                        }
                    }
                    PreviewControlSlot {
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
                    PreviewControlSlot {
                        IconButton(
                            onClick = { onNavigate(PlaybackNavigationDirection.NEXT) },
                            enabled = selection.request.navigationTarget(
                                PlaybackNavigationDirection.NEXT,
                            ) != null,
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next channel")
                        }
                    }
                    PreviewControlSlot {
                        IconButton(onClick = onOpenFullscreen) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Open full player")
                        }
                    }
                    PreviewControlSlot {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close preview")
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
