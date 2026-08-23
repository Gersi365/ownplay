package app.ownplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection

private enum class OwnPlaySection {
    LIVE,
    SOURCES,
    SETTINGS,
}

@Composable
fun OwnPlayApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
) {
    val sources by runtime.observeSources().collectAsState(initial = emptyList())
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()

    var section by remember { mutableStateOf(OwnPlaySection.SOURCES) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }

    LaunchedEffect(sources) {
        if (activeSourceId == null) {
            activeSourceId = sources.firstOrNull()?.sourceId
        } else if (sources.none { source -> source.sourceId == activeSourceId }) {
            activeSourceId = sources.firstOrNull()?.sourceId
            section = OwnPlaySection.SOURCES
        }

        val selectionSourceId = activeSelection?.request?.sourceId
        if (selectionSourceId != null && sources.none { source -> source.sourceId == selectionSourceId }) {
            activeSelection = null
            fullscreenSelection = null
            runtime.playbackController.stop()
        }
    }

    val openedFullscreen = fullscreenSelection
    if (openedFullscreen != null) {
        PlaybackScreen(
            selection = openedFullscreen,
            state = playbackState,
            trackState = playbackTrackState,
            videoOutput = runtime.playbackVideoOutput,
            onPlay = runtime.playbackController::play,
            onPause = runtime.playbackController::pause,
            onRetry = runtime.playbackController::retry,
            onAudioSelection = runtime.playbackTrackController::selectAudio,
            onSubtitleSelection = runtime.playbackTrackController::selectSubtitle,
            onNavigate = { direction ->
                openedFullscreen.navigate(direction)?.let { target ->
                    activeSelection = target
                    fullscreenSelection = target
                    runtime.playbackController.start(target.request)
                }
            },
            onReturnToChannels = {
                activeSourceId = openedFullscreen.request.sourceId
                section = OwnPlaySection.LIVE
                fullscreenSelection = null
            },
            onFullscreenStateChanged = onPlaybackFullscreenChanged,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OwnPlayTopShell(
            selected = section,
            activeSourceName = sources
                .firstOrNull { source -> source.sourceId == activeSourceId }
                ?.name,
            onSelected = { target ->
                section = when {
                    target == OwnPlaySection.LIVE && activeSourceId == null -> OwnPlaySection.SOURCES
                    else -> target
                }
            },
        )

        when (section) {
            OwnPlaySection.SOURCES -> SourcePickerScreen(
                sources = sources,
                selectedSourceId = activeSourceId,
                onSourceSelected = { sourceId ->
                    activeSourceId = sourceId
                    section = OwnPlaySection.LIVE
                },
            )

            OwnPlaySection.LIVE -> {
                val sourceId = activeSourceId
                if (sourceId == null) {
                    SourcePickerScreen(
                        sources = sources,
                        selectedSourceId = null,
                        onSourceSelected = { selected ->
                            activeSourceId = selected
                            section = OwnPlaySection.LIVE
                        },
                    )
                } else {
                    LiveRoute(
                        runtime = runtime,
                        sourceId = sourceId,
                        activeSelection = activeSelection,
                        playbackState = playbackState,
                        videoOutput = runtime.playbackVideoOutput,
                        onPlay = runtime.playbackController::play,
                        onPause = runtime.playbackController::pause,
                        onRetry = runtime.playbackController::retry,
                        onBackToSources = { section = OwnPlaySection.SOURCES },
                        onPreviewRequested = { selection ->
                            activeSelection = selection
                            runtime.playbackController.start(selection.request)
                        },
                        onPreviewClosed = {
                            activeSelection = null
                            runtime.playbackController.stop()
                        },
                        onOpenFullscreen = { selection ->
                            fullscreenSelection = selection
                        },
                        onNavigatePreview = { direction ->
                            activeSelection
                                ?.navigate(direction)
                                ?.let { target ->
                                    activeSelection = target
                                    runtime.playbackController.start(target.request)
                                }
                        },
                    )
                }
            }

            OwnPlaySection.SETTINGS -> SettingsScreen(
                sourceCount = sources.size,
                activeSourceName = sources
                    .firstOrNull { source -> source.sourceId == activeSourceId }
                    ?.name,
                hasActivePlayback = activeSelection != null,
                onOpenSources = { section = OwnPlaySection.SOURCES },
                onOpenLive = {
                    section = if (activeSourceId == null) {
                        OwnPlaySection.SOURCES
                    } else {
                        OwnPlaySection.LIVE
                    }
                },
                onStopPlayback = {
                    activeSelection = null
                    runtime.playbackController.stop()
                },
            )
        }
    }
}

@Composable
private fun OwnPlayTopShell(
    selected: OwnPlaySection,
    activeSourceName: String?,
    onSelected: (OwnPlaySection) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OwnPlay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = activeSourceName ?: "Your media hub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = selected.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTab(
                    label = "Live",
                    selected = selected == OwnPlaySection.LIVE,
                    onClick = { onSelected(OwnPlaySection.LIVE) },
                    modifier = Modifier.weight(1f),
                )
                SectionTab(
                    label = "Sources",
                    selected = selected == OwnPlaySection.SOURCES,
                    onClick = { onSelected(OwnPlaySection.SOURCES) },
                    modifier = Modifier.weight(1f),
                )
                SectionTab(
                    label = "Settings",
                    selected = selected == OwnPlaySection.SETTINGS,
                    onClick = { onSelected(OwnPlaySection.SETTINGS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
