package app.ownplay.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackBrowseContext
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import app.ownplay.player.ui.live.LiveBrowseScreen
import kotlinx.coroutines.launch

@Composable
internal fun LiveRoute(
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
    val mutationScope = rememberCoroutineScope()
    var editState by remember(sourceId) { mutableStateOf(ChannelEditState()) }
    var catalogRefreshing by remember(sourceId) { mutableStateOf(false) }
    var catalogRefreshFailed by remember(sourceId) { mutableStateOf(false) }

    suspend fun runCatalogRefresh(force: Boolean) {
        catalogRefreshing = true
        catalogRefreshFailed = false
        val result = if (force) {
            runtime.refreshLiveCatalog(sourceId)
        } else {
            runtime.ensureLiveCatalog(sourceId)
        }
        catalogRefreshFailed = result is SourceOnboardingResult.Failure
        catalogRefreshing = false
    }

    LaunchedEffect(sourceId) {
        runCatalogRefresh(force = false)
    }

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
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBackToSources) {
                Text("Sources")
            }
            TextButton(
                onClick = {
                    if (!catalogRefreshing) {
                        mutationScope.launch { runCatalogRefresh(force = true) }
                    }
                },
                enabled = !catalogRefreshing,
            ) {
                Text("Refresh")
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

        if (catalogRefreshing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Refreshing channels…",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (catalogRefreshFailed && browseState.channels.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Could not load channels from this source.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = {
                        mutationScope.launch { runCatalogRefresh(force = true) }
                    },
                ) {
                    Text("Retry")
                }
            }
        }

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
            onBulkAction = { action ->
                val selection = editState.selectedChannelIds
                if (selection.isNotEmpty()) {
                    mutationScope.launch {
                        runtime.executeChannelBulkAction(
                            sourceId = sourceId,
                            selectedChannelIds = selection,
                            action = action,
                        )
                    }
                }
            },
            onCreateGroup = { name ->
                mutationScope.launch { runtime.createCustomGroup(name) }
            },
            onRenameGroup = { groupId, name ->
                mutationScope.launch { runtime.renameCustomGroup(groupId, name) }
            },
            onDeleteGroup = { groupId ->
                mutationScope.launch { runtime.deleteCustomGroup(groupId) }
            },
            onSetLocalDisplayName = { channelId, name ->
                mutationScope.launch { runtime.setLocalDisplayName(sourceId, channelId, name) }
            },
            onClearLocalDisplayName = { channelId ->
                mutationScope.launch { runtime.clearLocalDisplayName(sourceId, channelId) }
            },
            onSetLogoOverride = { channelId, logoValue ->
                mutationScope.launch { runtime.setLogoOverride(sourceId, channelId, logoValue) }
            },
            onClearLogoOverride = { channelId ->
                mutationScope.launch { runtime.clearLogoOverride(sourceId, channelId) }
            },
            onManualMoveRelative = { channelId, anchorChannelId, placement ->
                mutationScope.launch {
                    runtime.moveChannelRelative(
                        sourceId = sourceId,
                        channelId = channelId,
                        anchorChannelId = anchorChannelId,
                        placement = placement,
                    )
                }
            },
            onFavoriteMoveRelative = { channelId, anchorChannelId, placement ->
                mutationScope.launch {
                    runtime.moveFavoriteRelative(
                        sourceId = sourceId,
                        channelId = channelId,
                        anchorChannelId = anchorChannelId,
                        placement = placement,
                    )
                }
            },
            onChannelSelected = { channelId ->
                val channel = browseState.channels.firstOrNull { item -> item.channelId == channelId }
                    ?: return@LiveBrowseScreen
                val browseContext = if (editState.isEditing) {
                    null
                } else {
                    LivePlaybackBrowseContext.capture(
                        sourceId = sourceId,
                        visibleChannels = browseState.channels,
                    )
                }
                when (
                    val action = LiveChannelSelectionRouter.route(
                        channel = channel,
                        isEditing = editState.isEditing,
                        browseContext = browseContext,
                    )
                ) {
                    is LiveChannelSelectionAction.ToggleEditSelection -> {
                        editState = ChannelEditReducer.toggleSelection(
                            state = editState,
                            channelId = action.channelId,
                        )
                    }
                    is LiveChannelSelectionAction.StartPlayback -> onPlaybackRequested(action.selection)
                }
            },
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
        )
    }
}
