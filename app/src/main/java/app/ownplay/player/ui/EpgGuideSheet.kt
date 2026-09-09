package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val initialFocus = EpgGuideFocusPolicy.initialFocus(
        isTelevision = isTelevision,
        loading = loading,
        failed = failed,
        programCount = timeline.programs.size,
        currentIndex = currentIndex,
    )
    val listState = rememberLazyListState()
    var selectedProgram by remember { mutableStateOf<EpgProgram?>(null) }

    LaunchedEffect(timeline.programs, timeline.current) {
        if (selectedProgram == null || selectedProgram !in timeline.programs) {
            selectedProgram = timeline.current ?: timeline.programs.firstOrNull()
        }
    }

    LaunchedEffect(
        isTelevision,
        loading,
        failed,
        timeline.programs.size,
        currentIndex,
    ) {
        when (initialFocus.target) {
            EpgGuideFocusTarget.NONE -> Unit
            EpgGuideFocusTarget.DONE -> {
                withFrameNanos { }
                doneFocusRequester.requestFocus()
            }
            EpgGuideFocusTarget.PROGRAM -> {
                val targetIndex = initialFocus.programIndex ?: return@LaunchedEffect
                listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
                withFrameNanos { }
                programFocusRequester.requestFocus()
            }
        }
    }

    if (isTelevision) {
        TvEpgGuideOverlay(
            channelName = channelName,
            timelinePrograms = timeline.programs,
            currentProgram = timeline.current,
            loading = loading,
            failed = failed,
            initialFocus = initialFocus,
            listState = listState,
            doneFocusRequester = doneFocusRequester,
            programFocusRequester = programFocusRequester,
            selectedProgram = selectedProgram,
            onProgramFocused = { selectedProgram = it },
            onDismiss = onDismiss,
        )
        return
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
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Full EPG",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                loading -> GuideMessage("Updating EPG…")
                failed -> GuideMessage("EPG is unavailable. Live playback remains available.")
                timeline.programs.isEmpty() -> GuideMessage("No guide data is available for this channel.")
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(
                            items = timeline.programs,
                            key = { index, program -> programKey(program, index) },
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
                                focusRequester = null,
                                onFocused = {},
                                onClick = { selectedProgram = program },
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
            onDismiss = { selectedProgram = null },
        )
    }
}

@Composable
private fun TvEpgGuideOverlay(
    channelName: String,
    timelinePrograms: List<EpgProgram>,
    currentProgram: EpgProgram?,
    loading: Boolean,
    failed: Boolean,
    initialFocus: EpgGuideInitialFocus,
    listState: androidx.compose.foundation.lazy.LazyListState,
    doneFocusRequester: FocusRequester,
    programFocusRequester: FocusRequester,
    selectedProgram: EpgProgram?,
    onProgramFocused: (EpgProgram) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp, vertical = 28.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "Full EPG",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(doneFocusRequester),
                    ) {
                        Text("Done")
                    }
                }

                when {
                    loading -> GuideMessage("Updating EPG…")
                    failed -> GuideMessage("EPG is unavailable. Live playback remains available.")
                    timelinePrograms.isEmpty() -> GuideMessage("No guide data is available for this channel.")
                    else -> Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(0.64f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                            tonalElevation = 0.dp,
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 10.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                itemsIndexed(
                                    items = timelinePrograms,
                                    key = { index, program -> programKey(program, index) },
                                ) { index, program ->
                                    val previous = timelinePrograms.getOrNull(index - 1)
                                    val day = program.startEpochSeconds?.let(::localDate)
                                    val previousDay = previous?.startEpochSeconds?.let(::localDate)
                                    if (day != null && day != previousDay) {
                                        DayHeader(day)
                                    }
                                    ProgramGuideRow(
                                        program = program,
                                        isCurrent = program == currentProgram,
                                        isPast = false,
                                        focusRequester = if (
                                            initialFocus.target == EpgGuideFocusTarget.PROGRAM &&
                                            index == initialFocus.programIndex
                                        ) {
                                            programFocusRequester
                                        } else {
                                            null
                                        },
                                        onFocused = { onProgramFocused(program) },
                                        onClick = { onProgramFocused(program) },
                                    )
                                }
                            }
                        }

                        TvGuideDetailPane(
                            program = selectedProgram ?: currentProgram ?: timelinePrograms.firstOrNull(),
                            modifier = Modifier
                                .weight(0.36f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGuideDetailPane(
    program: EpgProgram?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "PROGRAM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (program == null) {
                Text(
                    text = "Select a programme to see details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = timeRange(program),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                program.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
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
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
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
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(program.startEpochSeconds, program.endEpochSeconds, program.title) {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                focusRequester?.let { requester -> Modifier.focusRequester(requester) } ?: Modifier,
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = program.startLabel ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        focused || isCurrent -> MaterialTheme.colorScheme.primary
                        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
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
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        focused || isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
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
    onDismiss: () -> Unit,
) {
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun programKey(program: EpgProgram, index: Int): String =
    "${program.startEpochSeconds ?: Long.MIN_VALUE}:${program.endEpochSeconds ?: Long.MIN_VALUE}:${program.title}:$index"

private fun localDate(epochSeconds: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

private fun timeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "Time unavailable"
}

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
