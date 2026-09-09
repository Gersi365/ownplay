package app.ownplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackState

private const val TV_ON_DEMAND_SEEK_STEP_MILLIS = 10_000L

/**
 * OwnPlay TV-only transient controls for on-demand fullscreen playback.
 *
 * The surface deliberately avoids touch-derived Slider/IconButton chrome. Progress is informational,
 * actions keep fixed geometry, and focus is expressed through the OwnPlay TV color system only.
 * Playback/session ownership remains with the caller.
 */
@Composable
internal fun TvOnDemandPlaybackControls(
    title: String,
    playbackState: PlaybackState,
    currentPositionMs: Long,
    durationMs: Long,
    primaryFocusRequester: FocusRequester,
    onSeekBackward: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = PlaybackPresentationPolicy.controlsFor(playbackState)
    val playbackActionEnabled =
        playbackState is PlaybackState.Playing ||
            playbackState is PlaybackState.Paused ||
            (playbackState is PlaybackState.Failed && controls.canRetry)
    val seekEnabled = durationMs > 0L &&
        (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused)
    val safeDuration = durationMs.coerceAtLeast(0L)
    val rawPosition = currentPositionMs.coerceAtLeast(0L)
    val safePosition = if (safeDuration > 0L) rawPosition.coerceAtMost(safeDuration) else rawPosition
    val progress = if (safeDuration > 0L) {
        (safePosition.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val primaryLabel = when (playbackState) {
        is PlaybackState.Playing -> "Pause"
        is PlaybackState.Failed -> "Retry"
        else -> "Play"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (playbackState) {
                            is PlaybackState.Loading -> "Loading…"
                            is PlaybackState.Failed -> "Playback unavailable"
                            else -> "${formatTvOnDemandDuration(safePosition)} / ${formatTvOnDemandDuration(safeDuration)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playbackState is PlaybackState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = "Back · return",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    ),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvOnDemandAction(
                    label = "−10 s",
                    enabled = seekEnabled,
                    onClick = onSeekBackward,
                )
                TvOnDemandAction(
                    label = primaryLabel,
                    enabled = playbackActionEnabled,
                    focusRequester = primaryFocusRequester,
                    onClick = onPrimaryAction,
                )
                TvOnDemandAction(
                    label = "+10 s",
                    enabled = seekEnabled,
                    onClick = onSeekForward,
                )
                Spacer(Modifier.weight(1f))
                if (seekEnabled) {
                    Text(
                        text = "Seek step ${TV_ON_DEMAND_SEEK_STEP_MILLIS / 1_000L}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvOnDemandAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember(label) { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 132.dp)
            .height(54.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(14.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    focused -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun formatTvOnDemandDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
