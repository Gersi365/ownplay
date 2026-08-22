package app.ownplay.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.live.LiveBrowseScreen

private sealed interface OwnPlayRoute {
    data object Sources : OwnPlayRoute

    data class Live(
        val sourceId: String,
    ) : OwnPlayRoute

    data class Playback(
        val selection: LivePlaybackSelection,
    ) : OwnPlayRoute
}

@Composable
fun OwnPlayApp(
    runtime: OwnPlayAppRuntime,
) {
    val sources by runtime.observeSources().collectAsState(initial = emptyList())
    val playbackState by runtime.playbackController.state.collectAsState()
    var route by remember { mutableStateOf<OwnPlayRoute>(OwnPlayRoute.Sources) }
    var activeSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }

    LaunchedEffect(sources, route) {
        val routedSourceId = when (val current = route) {
            OwnPlayRoute.Sources -> null
            is OwnPlayRoute.Live -> current.sourceId
            is OwnPlayRoute.Playback -> current.selection.request.sourceId
        }
        if (routedSourceId != null && sources.none { source -> source.sourceId == routedSourceId }) {
            route = OwnPlayRoute.Sources
        }
    }

    when (val current = route) {
        OwnPlayRoute.Sources -> SourcePickerScreen(
            sources = sources,
            activeSelection = activeSelection,
            playbackState = playbackState,
            onSourceSelected = { sourceId -> route = OwnPlayRoute.Live(sourceId) },
            onResumePlayback = { selection -> route = OwnPlayRoute.Playback(selection) },
        )

        is OwnPlayRoute.Live -> LiveRoute(
            runtime = runtime,
            sourceId = current.sourceId,
            activeSelection = activeSelection,
            playbackState = playbackState,
            onBackToSources = { route = OwnPlayRoute.Sources },
            onPlaybackRequested = { selection ->
                activeSelection = selection
                runtime.playbackController.start(selection.request)
                route = OwnPlayRoute.Playback(selection)
            },
            onResumePlayback = { selection -> route = OwnPlayRoute.Playback(selection) },
        )

        is OwnPlayRoute.Playback -> PlaybackStatusScreen(
            selection = current.selection,
            state = playbackState,
            onReturnToChannels = {
                route = OwnPlayRoute.Live(current.selection.request.sourceId)
            },
        )
    }
}

@Composable
private fun SourcePickerScreen(
    sources: List<PlaylistSourceEntity>,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    onSourceSelected: (String) -> Unit,
    onResumePlayback: (LivePlaybackSelection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            Text(
                text = "OwnPlay",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your media. Your way.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (activeSelection != null && playbackState !is PlaybackState.Idle) {
                ActivePlaybackBar(
                    selection = activeSelection,
                    state = playbackState,
                    onOpen = { onResumePlayback(activeSelection) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (sources.isEmpty()) {
                Text(
                    text = "No playlists yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OwnPlay plays and organizes media sources you add. " +
                        "It does not provide channels, subscriptions, or IPTV services.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = sources,
                        key = PlaylistSourceEntity::sourceId,
                    ) { source ->
                        SourceRow(
                            source = source,
                            onOpen = { onSourceSelected(source.sourceId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: PlaylistSourceEntity,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = source.enabled, onClick = onOpen),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (source.enabled) "Ready" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (source.enabled) {
                TextButton(onClick = onOpen) {
                    Text("Live")
                }
            }
        }
    }
}

@Composable
private fun LiveRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    onBackToSources: () -> Unit,
    onPlaybackRequested: (LivePlaybackSelection) -> Unit,
    onResumePlayback: (LivePlaybackSelection) -> Unit,
) {
    val browseSession = remember(sourceId) { LiveBrowseSession() }
    val browseFlow = remember(sourceId) {
        browseSession.observe(runtime.observeLiveCatalog(sourceId))
    }
    val browseState by browseFlow.collectAsState(initial = LiveBrowseState())
    var editState by remember(sourceId) { mutableStateOf(ChannelEditState()) }

    LaunchedEffect(browseState.channels, editState.isEditing) {
        editState = ChannelEditReducer.retainAvailable(
            state = editState,
            availableChannelIds = browseState.channels.map { channel -> channel.channelId },
        )
    }

    BackHandler(onBack = onBackToSources)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBackToSources) {
                Text("Sources")
            }
            Spacer(modifier = Modifier.weight(1f))
            val resumable = activeSelection?.takeIf { selection ->
                selection.request.sourceId == sourceId && playbackState !is PlaybackState.Idle
            }
            if (resumable != null) {
                TextButton(onClick = { onResumePlayback(resumable) }) {
                    Text("Now playing")
                }
            }
        }
        HorizontalDivider()

        LiveBrowseScreen(
            state = browseState,
            onSearchChange = browseSession::updateSearch,
            onCategorySelected = browseSession::selectCategory,
            onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
            onOrderChanged = browseSession::setOrder,
            onCustomGroupSelected = browseSession::selectCustomGroup,
            onHiddenOnlyChanged = browseSession::setHiddenOnly,
            editState = editState,
            onEditModeChanged = { editing ->
                editState = if (editing) {
                    ChannelEditReducer.enter(editState)
                } else {
                    ChannelEditReducer.exit(editState)
                }
            },
            onChannelSelectionToggle = { channelId ->
                editState = ChannelEditReducer.toggleSelection(editState, channelId)
            },
            onSelectVisible = {
                editState = ChannelEditReducer.selectVisible(
                    state = editState,
                    visibleChannelIds = browseState.channels.map { channel -> channel.channelId },
                )
            },
            onClearSelection = {
                editState = ChannelEditReducer.clearSelection(editState)
            },
            onChannelSelected = { channelId ->
                val channel = browseState.channels.firstOrNull { item -> item.channelId == channelId }
                    ?: return@LiveBrowseScreen
                when (
                    val action = LiveChannelSelectionRouter.route(
                        channel = channel,
                        isEditing = editState.isEditing,
                    )
                ) {
                    is LiveChannelSelectionAction.ToggleEditSelection -> {
                        editState = ChannelEditReducer.toggleSelection(
                            state = editState,
                            channelId = action.channelId,
                        )
                    }
                    is LiveChannelSelectionAction.StartPlayback -> {
                        onPlaybackRequested(action.selection)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackStatusScreen(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    onReturnToChannels: () -> Unit,
) {
    BackHandler(onBack = onReturnToChannels)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onReturnToChannels) {
                Text("Back to channels")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = selection.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = playbackStatusLabel(state),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivePlaybackBar(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = playbackStatusLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

private fun playbackStatusLabel(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "Idle"
    is PlaybackState.Loading -> "Starting playback…"
    is PlaybackState.Playing -> "Playing"
    is PlaybackState.Paused -> "Paused"
    is PlaybackState.Failed -> when (state.failure.category) {
        PlaybackFailureCategory.NETWORK_UNAVAILABLE -> "Network unavailable"
        PlaybackFailureCategory.TIMEOUT -> "Playback timed out"
        PlaybackFailureCategory.AUTHENTICATION_FAILURE -> "Authentication failed"
        PlaybackFailureCategory.STREAM_UNAVAILABLE -> "Stream unavailable"
        PlaybackFailureCategory.UNSUPPORTED_MEDIA -> "Unsupported media"
        PlaybackFailureCategory.UNKNOWN -> "Playback failed"
    }
}
