package app.ownplay.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot

@Composable
internal fun EpgPanel(
    snapshot: EpgSnapshot?,
    loading: Boolean,
    failed: Boolean,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = snapshot?.current
    val next = snapshot?.next
    val guideAvailable = snapshot?.programs?.isNotEmpty() == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = guideAvailable && !loading,
                onClick = onOpenGuide,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Program guide",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (guideAvailable && !loading) {
                Text(
                    text = "View guide",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            loading -> StatusLine("Updating EPG…")
            failed -> StatusLine("EPG unavailable")
            current != null -> {
                ProgramLine(
                    prefix = "Now",
                    program = current,
                    emphasized = true,
                )
                if (next != null) {
                    HorizontalDivider()
                    ProgramLine(
                        prefix = "Next",
                        program = next,
                        emphasized = false,
                    )
                }
            }
            guideAvailable -> {
                val firstUpcoming = snapshot?.programs?.firstOrNull()
                if (firstUpcoming != null) {
                    ProgramLine(
                        prefix = "Guide",
                        program = firstUpcoming,
                        emphasized = false,
                    )
                }
            }
            else -> StatusLine("No EPG for this channel")
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProgramLine(
    prefix: String,
    program: EpgProgram,
    emphasized: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeRange(program),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$prefix · ${program.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (emphasized) {
                program.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun timeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "—"
}
