package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.epg.EpgTimelineProjector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpgGuideSheet(
    channelName: String,
    snapshot: EpgSnapshot?,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val doneFocusRequester = remember { FocusRequester() }
    val programFocusRequester = remember { FocusRequester() }
    val nowEpochSeconds = System.currentTimeMillis() / 1_000L
    val timeline = remember(snapshot, nowEpochSeconds) {
        EpgTimelineProjector.project(
            programs = snapshot?.programs.orEmpty(),
            nowEpochSeconds = nowEpochSeconds,
        )
    }
    val currentIndex = timeline.current?.let(timeline.programs::indexOf)?.takeIf { it >= 0 }
    val listState = rememberLazyListState()
    var selectedProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var lastFocusedProgramIndex by remember(snapshot) { mutableStateOf<Int?>(null) }
    val focusProgramIndex = epgGuideFocusIndex(
        programCount = timeline.programs.size,
        currentIndex = currentIndex,
        lastFocusedIndex = lastFocusedProgramIndex,
    )

    LaunchedEffect(
        isTelevision,
        focusProgramIndex,
        loading,
        failed,
        selectedProgram,
    ) {
        if (!isTelevision || selectedProgram != null) return@LaunchedEffect
        if (!loading && !failed && focusProgramIndex != null) {
            listState.scrollToItem((focusProgramIndex - 1).coerceAtLeast(0))
            withFrameNanos { }
            programFocusRequester.requestFocus()
        } else {
            withFrameNanos { }
            doneFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentIndex) {
        if (!isTelevision && currentIndex != null) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Program guide",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isTelevision) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(doneFocusRequester),
                    ) { Text("Done") }
                }
            }

            when {
                loading -> GuideMessage("Updating EPG…")
                failed -> GuideMessage("EPG is unavailable. Live playback remains available.")
                timeline.programs.isEmpty() -> GuideMessage("No guide data is available for this channel.")
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(
                            items = timeline.programs,
                            key = { index, program ->
                                "${program.startEpochSeconds ?: Long.MIN_VALUE}:${program.endEpochSeconds ?: Long.MIN_VALUE}:${program.title}:$index"
                            },
                        ) { index, program ->
                            val previous = timeline.programs.getOrNull(index - 1)
                            val day = program.startEpochSeconds?.let(::localDate)
                            val previousDay = previous?.startEpochSeconds?.let(::localDate)
                            if (day != null && day != previousDay) {
                                DayHeader(day)
                            }
                            ProgramGuideRow(
                                program = program,
                                isCurrent = program == timeline.current,
                                isPast = program in timeline.past,
                                focusRequester = programFocusRequester.takeIf {
                                    index == focusProgramIndex
                                },
                                onClick = {
                                    lastFocusedProgramIndex = index
                                    selectedProgram = program
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedProgram?.let { program ->
        ProgramDetailsDialog(
            program = program,
            isTelevision = isTelevision,
            onDismiss = { selectedProgram = null },
        )
    }
}

internal fun epgGuideFocusIndex(
    programCount: Int,
    currentIndex: Int?,
    lastFocusedIndex: Int?,
): Int? {
    if (programCount <= 0) return null
    return lastFocusedIndex
        ?.takeIf { it in 0 until programCount }
        ?: currentIndex?.takeIf { it in 0 until programCount }
        ?: 0
}

@Composable
private fun GuideMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> DAY_FORMATTER.format(day)
    }
    Text(
        text = label,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProgramGuideRow(
    program: EpgProgram,
    isCurrent: Boolean,
    isPast: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .mediaCardVisualTint()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = program.startLabel ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPast && !isCurrent) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                program.endLabel?.let { end ->
                    Text(
                        text = end,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (isCurrent) {
                    Text(
                        text = "NOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isPast && !isCurrent) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                program.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramDetailsDialog(
    program: EpgProgram,
    isTelevision: Boolean,
    onDismiss: () -> Unit,
) {
    val closeFocusRequester = remember(program) { FocusRequester() }

    LaunchedEffect(isTelevision, program) {
        if (!isTelevision) return@LaunchedEffect
        withFrameNanos { }
        closeFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(program.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = timeRange(program),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                program.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(description)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = if (isTelevision) {
                    Modifier.focusRequester(closeFocusRequester)
                } else {
                    Modifier
                },
            ) { Text("Close") }
        },
    )
}

private fun localDate(epochSeconds: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

private fun timeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "Time unavailable"
}

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
