package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.personalization.CategoryOrderFailureReason
import app.ownplay.player.personalization.CategoryOrderMutationResult
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
import app.ownplay.player.ui.live.LandscapeLiveWorkspace
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    var showEpgGuide by remember(sourceId) { mutableStateOf(false) }
    var showCategoryReorder by remember(sourceId) { mutableStateOf(false) }
    var categoryMutationInFlight by remember(sourceId) { mutableStateOf(false) }
    var categoryMutationError by remember(sourceId) { mutableStateOf<String?>(null) }
    var categoryOrderError by remember(sourceId) { mutableStateOf<String?>(null) }

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

    LaunchedEffect(preview?.request?.channelId) {
        showEpgGuide = false
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

    fun setEditMode(editing: Boolean) {
        if (editing) {
            browseSession.setIncludeHidden(true)
            editState = ChannelEditReducer.enter(editState)
        } else {
            showCategoryReorder = false
            if (selectedCategory?.isHidden == true) {
                browseSession.selectCategory(null)
            }
            if (!browseState.query.hiddenOnly) {
                browseSession.setIncludeHidden(false)
            }
            editState = ChannelEditReducer.exit(editState)
        }
    }

    fun executeBulkAction(action: ChannelBulkAction) {
        val selection = editState.selectedChannelIds
        if (selection.isEmpty()) return
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

    fun selectChannel(channelId: String) {
        val channel = browseState.channels.firstOrNull { item ->
            item.channelId == channelId
        } ?: return

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
    }

    fun toggleSelectedCategoryVisibility() {
        val category = selectedCategory ?: return
        if (categoryMutationInFlight) return
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
    }

    if (isLandscape) {
        LandscapeLiveWorkspace(
            runtime = runtime,
            sourceId = sourceId,
            browseSession = browseSession,
            state = browseState,
            editState = editState,
            onEditStateChange = { editState = it },
            preview = preview,
            playbackState = playbackState,
            videoOutput = videoOutput,
            epgSnapshot = epgSnapshot,
            epgLoading = loadingEpg || epgLookupLoading,
            epgFailed = epgRefreshFailed || epgLookupFailed,
            syncState = syncState,
            onPlay = onPlay,
            onPause = onPause,
            onRetry = onRetry,
            onPreviewRequested = onPreviewRequested,
            onPreviewClosed = onPreviewClosed,
            onOpenFullscreen = onOpenFullscreen,
            onNavigatePreview = onNavigatePreview,
            onOpenEpgGuide = { showEpgGuide = true },
            onReorderCategoriesRequested = {
                if (editState.isEditing) {
                    categoryOrderError = null
                    showCategoryReorder = true
                }
            },
            onOpenSettings = onOpenSettings,
        )
    } else {
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
                                onOpenGuide = { showEpgGuide = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                onOpenGuide = { showEpgGuide = true },
                            )
                        }
                    }
                }
            }

            when {
                loadingChannels -> InlineSyncStatus(
                    text = if (browseState.catalogChannelCount == 0) {
                        "Loading channels…"
                    } else {
                        "Updating channels…"
                    },
                )
                loadingEpg -> InlineSyncStatus(text = "Updating EPG…")
                channelRefreshFailed -> InlineErrorStatus(
                    text = "Channel refresh failed. Existing channels were kept.",
                    actionLabel = "Retry",
                    onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                )
                epgRefreshFailed -> InlineErrorStatus(
                    text = "EPG unavailable. Live remains usable.",
                    actionLabel = "Retry EPG",
                    onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                )
            }

            categoryOrderError?.let { error ->
                InlineErrorStatus(
                    text = error,
                    actionLabel = "Dismiss",
                    onAction = { categoryOrderError = null },
                )
            }

            if (editState.isEditing && selectedCategory != null) {
                CategoryEditBar(
                    category = selectedCategory,
                    mutationInFlight = categoryMutationInFlight,
                    errorMessage = categoryMutationError,
                    onToggleVisibility = ::toggleSelectedCategoryVisibility,
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
                    playingChannelId = preview?.request?.channelId,
                    onReorderCategoriesRequested = {
                        if (editState.isEditing) {
                            categoryOrderError = null
                            showCategoryReorder = true
                        }
                    },
                    onEditModeChanged = ::setEditMode,
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
                    onBulkAction = ::executeBulkAction,
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
                    onChannelSelected = ::selectChannel,
                    modifier = Modifier
                        .weight(1f)
                        .navigationBarsPadding(),
                )
            }
        }
    }

    if (showEpgGuide && preview != null) {
        EpgGuideSheet(
            channelName = preview.displayName,
            snapshot = epgSnapshot,
            loading = loadingEpg || epgLookupLoading,
            failed = epgRefreshFailed || epgLookupFailed,
            onDismiss = { showEpgGuide = false },
        )
    }

    if (showCategoryReorder && editState.isEditing) {
        CategoryReorderSheet(
            categories = browseState.categories,
            onOrderChanged = { orderedKeys ->
                mutationScope.launch {
                    when (
                        val result = runtime.setCategoryOrder(
                            sourceId = sourceId,
                            orderedCategoryKeys = orderedKeys,
                        )
                    ) {
                        is CategoryOrderMutationResult.Success -> categoryOrderError = null
                        is CategoryOrderMutationResult.Failure -> {
                            categoryOrderError = categoryOrderFailureLabel(result)
                        }
                    }
                }
            },
            onDismiss = { showCategoryReorder = false },
        )
    }
}

@Composable
private fun CategoryEditBar(
    category: LiveCategory,
    mutationInFlight: Boolean,
    errorMessage: String?,
    onToggleVisibility: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    errorMessage != null -> errorMessage
                    category.isHidden -> "Hidden category · visible while editing"
                    else -> "Category visibility"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (mutationInFlight) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            TextButton(onClick = onToggleVisibility) {
                Text(if (category.isHidden) "Unhide" else "Hide")
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

private fun categoryOrderFailureLabel(
    failure: CategoryOrderMutationResult.Failure,
): String = when (failure.reason) {
    CategoryOrderFailureReason.PERSISTENCE_FAILURE ->
        "Could not save category order. The channel list remains usable."
    CategoryOrderFailureReason.INVALID_SOURCE_ID,
    CategoryOrderFailureReason.INVALID_CATEGORY_ORDER,
    -> "Category order could not be saved."
}

@Composable
private fun InlineSyncStatus(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InlineErrorStatus(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
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
