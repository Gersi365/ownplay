package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState

private const val SECTION_MOTION_MILLIS = 200

private enum class OwnPlaySection {
    LIVE,
    SETTINGS,
}

@Composable
fun OwnPlayApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
    val syncState by runtime.sourceSyncState.collectAsState()
    val playbackState by runtime.playbackController.state.collectAsState()
    val playbackTrackState by runtime.playbackTrackController.state.collectAsState()

    var section by remember { mutableStateOf(OwnPlaySection.LIVE) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var fullscreenSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }

    LaunchedEffect(summaries) {
        val ids = summaries.map { it.sourceId }.toSet()
        activeSourceId = when {
            activeSourceId in ids -> activeSourceId
            summaries.isNotEmpty() -> summaries.first().sourceId
            else -> null
        }
        val selectionSourceId = activeSelection?.request?.sourceId
        if (selectionSourceId != null && selectionSourceId !in ids) {
            activeSelection = null
            fullscreenSelection = null
            runtime.playbackController.stop()
        }
    }

    val previewActive =
        section == OwnPlaySection.LIVE &&
            activeSelection != null &&
            fullscreenSelection == null
    val playbackSurfaceActive = previewActive || fullscreenSelection != null
    val compactLiveLandscape =
        section == OwnPlaySection.LIVE &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(playbackSurfaceActive) {
        onPlaybackSurfaceActiveChanged(playbackSurfaceActive)
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

    val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OwnPlayHeader(
                section = section,
                activeSourceId = activeSourceId,
                summaries = summaries,
                syncState = syncState,
                compact = compactLiveLandscape,
                onSettingsRequested = { section = OwnPlaySection.SETTINGS },
                onSourceSelected = { sourceId ->
                    if (sourceId != activeSourceId) {
                        activeSourceId = sourceId
                        activeSelection = null
                        fullscreenSelection = null
                        runtime.playbackController.stop()
                    }
                },
            )
        },
        bottomBar = {
            if (!compactLiveLandscape) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    NavigationBarItem(
                        selected = section == OwnPlaySection.LIVE,
                        onClick = { section = OwnPlaySection.LIVE },
                        icon = { Icon(Icons.Filled.LiveTv, contentDescription = "Live") },
                        label = { Text("Live") },
                    )
                    NavigationBarItem(
                        selected = section == OwnPlaySection.SETTINGS,
                        onClick = { section = OwnPlaySection.SETTINGS },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = section,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(
                        animationSpec = tween(SECTION_MOTION_MILLIS),
                        initialOffsetX = { width -> width / 18 },
                    ) + fadeIn(tween(SECTION_MOTION_MILLIS))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(SECTION_MOTION_MILLIS),
                            targetOffsetX = { width -> -width / 28 },
                        ) + fadeOut(tween(130)))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(SECTION_MOTION_MILLIS),
                        initialOffsetX = { width -> -width / 18 },
                    ) + fadeIn(tween(SECTION_MOTION_MILLIS))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(SECTION_MOTION_MILLIS),
                            targetOffsetX = { width -> width / 28 },
                        ) + fadeOut(tween(130)))
                }
            },
            label = "ownPlaySection",
        ) { targetSection ->
            when (targetSection) {
                OwnPlaySection.LIVE -> {
                    val sourceId = activeSourceId
                    if (sourceId == null) {
                        LiveNoSourceScreen(
                            syncState = syncState,
                            onAddPlaylist = { section = OwnPlaySection.SETTINGS },
                        )
                    } else {
                        LiveRoute(
                            runtime = runtime,
                            sourceId = sourceId,
                            activeSelection = activeSelection,
                            playbackState = playbackState,
                            videoOutput = runtime.playbackVideoOutput,
                            syncState = syncState,
                            onPlay = runtime.playbackController::play,
                            onPause = runtime.playbackController::pause,
                            onRetry = runtime.playbackController::retry,
                            onOpenSettings = { section = OwnPlaySection.SETTINGS },
                            onPreviewRequested = { selection ->
                                activeSelection = selection
                                runtime.playbackController.start(selection.request)
                            },
                            onPreviewClosed = {
                                activeSelection = null
                                runtime.playbackController.stop()
                            },
                            onOpenFullscreen = { selection -> fullscreenSelection = selection },
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
                    runtime = runtime,
                    summaries = summaries,
                    syncState = syncState,
                    activeSourceName = activeSummary?.name,
                    hasActivePlayback = activeSelection != null,
                    onOpenLive = { section = OwnPlaySection.LIVE },
                    onOpenSourceInLive = { sourceId ->
                        if (sourceId != activeSourceId) {
                            activeSelection = null
                            fullscreenSelection = null
                            runtime.playbackController.stop()
                        }
                        activeSourceId = sourceId
                        section = OwnPlaySection.LIVE
                    },
                    onStopPlayback = {
                        activeSelection = null
                        runtime.playbackController.stop()
                    },
                )
            }
        }
    }
}

@Composable
private fun OwnPlayHeader(
    section: OwnPlaySection,
    activeSourceId: String?,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    compact: Boolean,
    onSettingsRequested: () -> Unit,
    onSourceSelected: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (compact) 1.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = if (compact) 8.dp else 16.dp,
                    vertical = if (compact) 0.dp else 7.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(if (compact) 8.dp else 11.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "OP",
                    modifier = Modifier.padding(
                        horizontal = if (compact) 7.dp else 9.dp,
                        vertical = if (compact) 3.dp else 6.dp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }

            if (compact && section == OwnPlaySection.LIVE) {
                Box(modifier = Modifier.weight(1f)) {
                    if (summaries.isNotEmpty()) {
                        LiveSourceSelector(
                            activeSourceId = activeSourceId,
                            summaries = summaries,
                            compact = true,
                            onSourceSelected = onSourceSelected,
                        )
                    } else {
                        Text("Live", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OwnPlay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    when {
                        section == OwnPlaySection.LIVE && summaries.isNotEmpty() -> LiveSourceSelector(
                            activeSourceId = activeSourceId,
                            summaries = summaries,
                            compact = false,
                            onSourceSelected = onSourceSelected,
                        )
                        section == OwnPlaySection.SETTINGS -> Text(
                            text = "Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Unit
                    }
                }
            }

            when (syncState.stage) {
                SourceSyncStage.LoadingChannels,
                SourceSyncStage.LoadingEpg,
                -> CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 15.dp else 20.dp),
                    strokeWidth = 2.dp,
                )
                else -> Unit
            }

            if (compact && section == OwnPlaySection.LIVE) {
                IconButton(
                    onClick = onSettingsRequested,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveSourceSelector(
    activeSourceId: String?,
    summaries: List<PlaylistSourceSummary>,
    compact: Boolean,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = summaries.firstOrNull { it.sourceId == activeSourceId } ?: summaries.first()

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = if (compact) Modifier.height(32.dp) else Modifier,
            contentPadding = if (compact) {
                PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            } else {
                PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            },
        ) {
            Text(
                text = active.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Choose playlist",
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            summaries.forEach { summary ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = summary.name,
                                fontWeight = if (summary.sourceId == active.sourceId) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Text(
                                text = "${summary.channelCount} channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSourceSelected(summary.sourceId)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveNoSourceScreen(
    syncState: SourceSyncState,
    onAddPlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Live TV",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (syncState.stage == SourceSyncStage.LoadingChannels) {
                    Text(
                        text = "Loading channels…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "No playlist configured",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Add a playlist from Settings to start watching Live TV.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onAddPlaylist) { Text("Add playlist") }
                }
            }
        }
    }
}
