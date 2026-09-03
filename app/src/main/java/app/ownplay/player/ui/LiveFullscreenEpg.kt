package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
            value = try {
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
            firstLookup = false
            delay(FULLSCREEN_EPG_REFRESH_MILLIS)
        }
    }

    val snapshot = epgState.snapshot
    val current = snapshot?.current
    val next = snapshot?.next
    val firstUpcoming = snapshot?.programs?.firstOrNull()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = selection.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                epgState.loading -> FullscreenEpgStatus("Updating EPG…")
                epgState.failed -> FullscreenEpgStatus("EPG unavailable")
                current != null -> {
                    FullscreenEpgProgramLine(
                        prefix = "Now",
                        program = current,
                        emphasized = true,
                    )
                    if (next != null) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                        FullscreenEpgProgramLine(
                            prefix = "Next",
                            program = next,
                            emphasized = false,
                        )
                    }
                }
                firstUpcoming != null -> FullscreenEpgProgramLine(
                    prefix = "Next",
                    program = firstUpcoming,
                    emphasized = false,
                )
                else -> FullscreenEpgStatus("No EPG for this channel")
            }
        }
    }
}

@Composable
private fun FullscreenEpgProgramLine(
    prefix: String,
    program: EpgProgram,
    emphasized: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fullscreenEpgTimeRange(program),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
        )
        Text(
            text = "$prefix · ${program.title}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

private fun fullscreenEpgTimeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "—"
}
