package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.personalization.CategoryVisibilityFailureReason
import app.ownplay.player.personalization.CategoryVisibilityMutationResult
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelBulkActionExecutionResult
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.personalization.ChannelVisibilityMutationResult
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackBrowseContext
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.ui.live.LiveBrowseScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LiveRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    syncState: SourceSyncState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviewRequested: (LivePlaybackSelection) -> Unit,
    onPreviewClosed: () -> Unit,
    onOpenFullscreen: (LivePlaybackSelection) -> Unit,
    onNavigatePreview: (PlaybackNavigationDirection) -> Unit,
) {
    val browseSession = remember(sourceId) { LiveBrowseSession() }
    val browseFlow = remember(sourceId) {
        browseSession.observe(runtime.observeLiveCatalog(sourceId))
    }
    val browseState by browseFlow.collectAsState(initial = LiveBrowseState())
    val mutationScope = rememberCoroutineScope()
    var editState by remember(sourceId) { mutableStateOf(ChannelEditState()) }
    val preview = activeSelection?.takeIf { selection ->
        selection.request.sourceId == sourceId
    }
    var epgSnapshot by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf<EpgSnapshot?>(null)
    }
    var epgLookupLoading by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf(false)
    }
    var epgLookupFailed by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf(false)
    }
    var categoryMutationInFlight by remember(sourceId) { mutableStateOf(false) }
    var categoryMutationError by remember(sourceId) { mutableStateOf<String?>(null) }

    val syncForThisSource = syncState.sourceId == sourceId
    val loadingChannels = syncForThisSource && syncState.stage == SourceSyncStage.LoadingChannels
    val loadingEpg = syncForThisSource && syncState.stage == SourceSyncStage.LoadingEpg
    val channelRefreshFailed = syncForThisSource && syncState.stage == SourceSyncStage.ChannelsFailed
    val epgRefreshFailed = syncForThisSource && syncState.stage == SourceSyncStage.EpgFailed
    val selectedCategory = browseState.query.categoryKey?.let { categoryKey ->
        browseState.categories.firstOrNull { category ->
            category.providerCategoryKey == categoryKey
        }
    }

    LaunchedEffect(browseState.query.categoryKey) {
        categoryMutationError = null
    }

    LaunchedEffect(preview?.request?.channelId, syncState.sourceId, syncState.stage) {
        val selected = preview
        if (selected == null) {
            epgSnapshot = null
            epgLookupLoading = false
            epgLookupFailed = false
            return@LaunchedEffect
        }
        if (loadingEpg) {
            epgSnapshot = null
            epgLookupLoading = false
            epgLookupFailed = false
            return@LaunchedEffect
        }
        epgLookupLoading = true
        epgLookupFailed = false
        try {
            epgSnapshot = runtime.epgSnapshot(
                sourceId = sourceId,
                channelId = selected.request.channelId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            epgSnapshot = null
            epgLookupFailed = true
        } finally {
            epgLookupLoading = false
        }
    }

    LaunchedEffect(browseState.channels, editState.isEditing) {
        editState = ChannelEditReducer.retainAvailable(
            state = editState,
            availableChannelIds = browseState.channels.map { channel -> channel.channelId },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (preview != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val wide = maxWidth >= 700.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LivePreviewPanel(
                            selection = preview,
                            state = playbackState,
                            videoOutput = videoOutput,
                            onPlay = onPlay,
                            onPause = onPause,
                            onRetry = onRetry,
                            onNavigate = onNavigatePreview,
                            onOpenFullscreen = { onOpenFullscreen(preview) },
                            onClose = onPreviewClosed,
                            modifier = Modifier.weight(1.45f),
                        )
                        EpgPanel(
                            snapshot = epgSnapshot,
                            loading = loadingEpg || epgLookupLoading,
                            failed = epgRefreshFailed || epgLookupFailed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LivePreviewPanel(
                            selection = preview,
                            state = playbackState,
                            videoOutput = videoOutput,
                            onPlay = onPlay,
                            onPause = onPause,
                            onRetry = onRetry,
                            onNavigate = onNavigatePreview,
                            onOpenFullscreen = { onOpenFullscreen(preview) },
                            onClose = onPreviewClosed,
                        )
                        EpgPanel(
                            snapshot = epgSnapshot,
                            loading = loadingEpg || epgLookupLoading,
                            failed = epgRefreshFailed || epgLookupFailed,
                        )
                    }
                }
            }
        }

        when {
            loadingChannels -> SyncBanner(
                text = "Loading channels…",
                supporting = if (browseState.catalogChannelCount == 0) {
                    "Building the Live catalog."
                } else {
                    "Refreshing in the background. Existing channels stay available."
                },
            )
            loadingEpg -> SyncBanner(
                text = "Loading EPG…",
                supporting = "Channels are ready. Program guide data is loading now.",
            )
            channelRefreshFailed -> ErrorBanner(
                text = "Channel refresh failed. Existing channels were kept.",
                actionLabel = "Retry",
                onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
            )
            epgRefreshFailed -> ErrorBanner(
                text = "EPG unavailable. Live channels remain usable.",
                actionLabel = "Retry EPG",
                onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
            )
        }

        if (editState.isEditing && selectedCategory != null) {
            CategoryEditBar(
                category = selectedCategory,
                mutationInFlight = categoryMutationInFlight,
                errorMessage = categoryMutationError,
                onToggleVisibility = {
                    if (categoryMutationInFlight) return@CategoryEditBar
                    val category = selectedCategory
                    val previewCategoryKey = preview?.request?.channelId?.let { channelId ->
                        browseState.channelCategoryKeyById[channelId]
                    }
                    categoryMutationInFlight = true
                    categoryMutationError = null
                    mutationScope.launch {
                        try {
                            val result = if (category.isHidden) {
                                runtime.unhideCategory(
                                    sourceId = sourceId,
                                    providerCategoryKey = category.providerCategoryKey,
                                )
                            } else {
                                runtime.hideCategory(
                                    sourceId = sourceId,
                                    providerCategoryKey = category.providerCategoryKey,
                                )
                            }
                            when (result) {
                                is CategoryVisibilityMutationResult.Success -> {
                                    if (
                                        result.hidden &&
                                        previewCategoryKey == result.providerCategoryKey
                                    ) {
                                        onPreviewClosed()
                                    }
                                }
                                is CategoryVisibilityMutationResult.Failure -> {
                                    categoryMutationError = categoryVisibilityFailureLabel(result)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            categoryMutationError = "Could not update this category. Try again."
                        } finally {
                            categoryMutationInFlight = false
                        }
                    }
                },
            )
        }

        if (browseState.catalogChannelCount == 0 && !loadingChannels) {
            LiveCatalogEmptyState(
                failed = channelRefreshFailed,
                onRetry = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        } else {
            LiveBrowseScreen(
                state = browseState,
                onSearchChange = browseSession::updateSearch,
                onCategorySelected = browseSession::selectCategory,
                onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
                onOrderChanged = browseSession::setOrder,
                onCustomGroupSelected = browseSession::selectCustomGroup,
                onHiddenOnlyChanged = { enabled ->
                    browseSession.setHiddenOnly(
                        enabled = enabled,
                        includeHiddenWhenDisabled = editState.isEditing,
                    )
                },
                editState = editState,
                onEditModeChanged = { editing ->
                    if (editing) {
                        browseSession.setIncludeHidden(true)
                        editState = ChannelEditReducer.enter(editState)
                    } else {
                        if (selectedCategory?.isHidden == true) {
                            browseSession.selectCategory(null)
                        }
                        if (!browseState.query.hiddenOnly) {
                            browseSession.setIncludeHidden(false)
                        }
                        editState = ChannelEditReducer.exit(editState)
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
                            val result = runtime.executeChannelBulkAction(
                                sourceId = sourceId,
                                selectedChannelIds = selection,
                                action = action,
                            )
                            val activeChannelWasHidden =
                                action == ChannelBulkAction.Hide &&
                                    result is ChannelBulkActionExecutionResult.Visibility &&
                                    result.result is ChannelVisibilityMutationResult.Success &&
                                    preview?.request?.channelId in selection
                            if (activeChannelWasHidden) {
                                onPreviewClosed()
                            }
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
                    val channel = browseState.channels.firstOrNull { item ->
                        item.channelId == channelId
                    } ?: return@LiveBrowseScreen

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

                        is LiveChannelSelectionAction.StartPlayback -> {
                            onPreviewRequested(action.selection)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun CategoryEditBar(
    category: LiveCategory,
    mutationInFlight: Boolean,
    errorMessage: String?,
    onToggleVisibility: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (category.isHidden) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        errorMessage != null -> errorMessage
                        category.isHidden -> "Hidden category. Its channels stay visible while editing."
                        else -> "Hide this category and all channels inside it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else if (category.isHidden) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            if (mutationInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onToggleVisibility) {
                    Text(if (category.isHidden) "Unhide category" else "Hide category")
                }
            }
        }
    }
}

private fun categoryVisibilityFailureLabel(
    failure: CategoryVisibilityMutationResult.Failure,
): String = when (failure.reason) {
    CategoryVisibilityFailureReason.CATEGORY_NOT_FOUND ->
        "This category is no longer available. Refresh and try again."
    CategoryVisibilityFailureReason.PERSISTENCE_FAILURE ->
        "Could not save category visibility. Try again."
    CategoryVisibilityFailureReason.INVALID_SOURCE_ID,
    CategoryVisibilityFailureReason.EMPTY_CATEGORY_KEY,
    -> "Category visibility is unavailable for this item."
}

@Composable
private fun SyncBanner(
    text: String,
    supporting: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Column {
                Text(text, fontWeight = FontWeight.SemiBold)
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LiveCatalogEmptyState(
    failed: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "0 channels",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (failed) {
                "The playlist exists, but channels could not be refreshed."
            } else {
                "This playlist does not currently contain loaded Live channels."
            },
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Retry channels") }
            TextButton(onClick = onOpenSettings) { Text("Playlist settings") }
        }
    }
}
