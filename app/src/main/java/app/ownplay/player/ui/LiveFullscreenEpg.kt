package app.ownplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.playback.LivePlaybackSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val FULLSCREEN_EPG_REFRESH_MILLIS = 30_000L
private const val FULLSCREEN_EPG_CLOCK_TICK_MILLIS = 5_000L
private const val FULLSCREEN_EPG_MIN_REFRESH_MILLIS = 1_000L
private const val FULLSCREEN_EPG_BOUNDARY_GRACE_MILLIS = 250L

private data class FullscreenEpgUiState(
    val snapshot: EpgSnapshot? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Read-only Live EPG surface for the full player.
 *
 * This component deliberately owns no playback controls or playback state. It reads the same
 * source/channel EPG repository used by Live browsing and refreshes the current/next projection
 * while the full player remains open.
 */
@Composable
internal fun LiveFullscreenEpg(
    runtime: OwnPlayAppRuntime,
    selection: LivePlaybackSelection,
    modifier: Modifier = Modifier,
) {
    val sourceId = selection.request.sourceId
    val channelId = selection.request.channelId
    val epgState by produceState(
        initialValue = FullscreenEpgUiState(),
        key1 = sourceId,
        key2 = channelId,
    ) {
        var firstLookup = true
        while (true) {
            if (firstLookup) {
                value = FullscreenEpgUiState(loading = true)
            }
            val nextState = try {
                FullscreenEpgUiState(
                    snapshot = runtime.epgSnapshot(
                        sourceId = sourceId,
                        channelId = channelId,
                    ),
                    loading = false,
                    failed = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                FullscreenEpgUiState(
                    snapshot = null,
                    loading = false,
                    failed = true,
                )
            }
            value = nextState
            firstLookup = false
            delay(
                fullscreenEpgRefreshDelayMillis(
                    currentProgramEndEpochSeconds = nextState.snapshot?.current?.endEpochSeconds,
                    nowEpochSeconds = System.currentTimeMillis() / 1_000L,
                ),
            )
        }
    }
    val nowEpochSeconds by produceState(
        initialValue = System.currentTimeMillis() / 1_000L,
        key1 = sourceId,
        key2 = channelId,
    ) {
        while (true) {
            value = System.currentTimeMillis() / 1_000L
            delay(FULLSCREEN_EPG_CLOCK_TICK_MILLIS)
        }
    }

    val snapshot = epgState.snapshot
    val current = snapshot?.current
    val next = snapshot?.next
    val firstUpcoming = snapshot?.programs?.firstOrNull()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.74f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = selection.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                epgState.loading -> FullscreenEpgStatus("Updating EPG…")
                epgState.failed -> FullscreenEpgStatus("EPG unavailable")
                current != null -> {
                    FullscreenCurrentProgram(
                        program = current,
                        nowEpochSeconds = nowEpochSeconds,
                    )
                    if (next != null) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        FullscreenUpcomingProgram(program = next)
                    }
                }
                firstUpcoming != null -> FullscreenUpcomingProgram(program = firstUpcoming)
                else -> FullscreenEpgStatus("No EPG for this channel")
            }
        }
    }
}

@Composable
private fun FullscreenCurrentProgram(
    program: EpgProgram,
    nowEpochSeconds: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = program.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = fullscreenEpgTimeRange(program),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }

        fullscreenEpgProgress(program, nowEpochSeconds)?.let { progress ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun FullscreenUpcomingProgram(program: EpgProgram) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NEXT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
        )
        Text(
            text = program.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = fullscreenEpgTimeRange(program),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
        )
    }
}

@Composable
private fun FullscreenEpgStatus(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.76f),
        maxLines = 1,
    )
}

internal fun fullscreenEpgProgress(
    program: EpgProgram,
    nowEpochSeconds: Long,
): Float? {
    val start = program.startEpochSeconds ?: return null
    val end = program.endEpochSeconds ?: return null
    val duration = end - start
    if (duration <= 0L) return null
    return ((nowEpochSeconds - start).toDouble() / duration.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

internal fun fullscreenEpgRefreshDelayMillis(
    currentProgramEndEpochSeconds: Long?,
    nowEpochSeconds: Long,
): Long {
    val end = currentProgramEndEpochSeconds ?: return FULLSCREEN_EPG_REFRESH_MILLIS
    val secondsUntilBoundary = end - nowEpochSeconds
    if (secondsUntilBoundary <= 0L) return FULLSCREEN_EPG_MIN_REFRESH_MILLIS
    if (secondsUntilBoundary >= FULLSCREEN_EPG_REFRESH_MILLIS / 1_000L) {
        return FULLSCREEN_EPG_REFRESH_MILLIS
    }

    return (secondsUntilBoundary * 1_000L + FULLSCREEN_EPG_BOUNDARY_GRACE_MILLIS)
        .coerceAtLeast(FULLSCREEN_EPG_MIN_REFRESH_MILLIS)
}

private fun fullscreenEpgTimeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "—"
}
