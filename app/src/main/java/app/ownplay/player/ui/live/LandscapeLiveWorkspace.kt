package app.ownplay.player.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.personalization.CategoryVisibilityFailureReason
import app.ownplay.player.personalization.CategoryVisibilityMutationResult
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelBulkActionExecutionResult
import app.ownplay.player.personalization.ChannelDragTarget
import app.ownplay.player.personalization.ChannelDragTargetResolver
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.personalization.ChannelVisibilityMutationResult
import app.ownplay.player.personalization.ManualOrderPlacement
import app.ownplay.player.personalization.VisibleChannelBounds
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackBrowseContext
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.ui.LivePreviewPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MOTION_FAST = 150
private const val MOTION_MEDIUM = 220

@Composable
internal fun LandscapeLiveWorkspace(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    browseSession: LiveBrowseSession,
    state: LiveBrowseState,
    editState: ChannelEditState,
    onEditStateChange: (ChannelEditState) -> Unit,
    preview: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    epgSnapshot: EpgSnapshot?,
    epgLoading: Boolean,
    epgFailed: Boolean,
    syncState: SourceSyncState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onPreviewRequested: (LivePlaybackSelection) -> Unit,
    onPreviewClosed: () -> Unit,
    onOpenFullscreen: (LivePlaybackSelection) -> Unit,
    onNavigatePreview: (PlaybackNavigationDirection) -> Unit,
    onOpenEpgGuide: () -> Unit,
    onReorderCategoriesRequested: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val mutationScope = rememberCoroutineScope()
    val selectedCategory = state.query.categoryKey?.let { key ->
        state.categories.firstOrNull { it.providerCategoryKey == key }
    }
    var categoryMutationInFlight by remember(sourceId) { mutableStateOf(false) }
    var categoryMutationError by remember(sourceId) { mutableStateOf<String?>(null) }

    val syncForThisSource = syncState.sourceId == sourceId
    val loadingChannels = syncForThisSource && syncState.stage == SourceSyncStage.LoadingChannels
    val channelRefreshFailed = syncForThisSource && syncState.stage == SourceSyncStage.ChannelsFailed

    fun setEditing(editing: Boolean) {
        if (editing) {
            val editOrder = if (state.query.favoritesOnly) {
                LiveBrowseOrder.FAVORITE_ORDER
            } else {
                LiveBrowseOrder.MY_ORDER
            }
            if (state.query.order != editOrder) browseSession.setOrder(editOrder)
            browseSession.setIncludeHidden(true)
            onEditStateChange(ChannelEditReducer.enter(editState))
        } else {
            if (selectedCategory?.isHidden == true) browseSession.selectCategory(null)
            if (!state.query.hiddenOnly) browseSession.setIncludeHidden(false)
            onEditStateChange(ChannelEditReducer.exit(editState))
        }
    }

    fun selectChannel(channelId: String) {
        val channel = state.channels.firstOrNull { it.channelId == channelId } ?: return
        val browseContext = if (editState.isEditing) {
            null
        } else {
            LivePlaybackBrowseContext.capture(
                sourceId = sourceId,
                visibleChannels = state.channels,
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
                onEditStateChange(
                    ChannelEditReducer.toggleSelection(editState, action.channelId),
                )
            }
            is LiveChannelSelectionAction.StartPlayback -> onPreviewRequested(action.selection)
        }
    }

    fun runBulkAction(action: ChannelBulkAction) {
        val selection = editState.selectedChannelIds
        if (selection.isEmpty()) return
        mutationScope.launch {
            val result = runtime.executeChannelBulkAction(
                sourceId = sourceId,
                selectedChannelIds = selection,
                action = action,
            )
            val activeWasHidden =
                action == ChannelBulkAction.Hide &&
                    result is ChannelBulkActionExecutionResult.Visibility &&
                    result.result is ChannelVisibilityMutationResult.Success &&
                    preview?.request?.channelId in selection
            if (activeWasHidden) onPreviewClosed()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LandscapeCategoryPane(
            state = state,
            editState = editState,
            selectedCategory = selectedCategory,
            categoryMutationInFlight = categoryMutationInFlight,
            categoryMutationError = categoryMutationError,
            onCategorySelected = {
                categoryMutationError = null
                browseSession.selectCategory(it)
            },
            onReorderCategoriesRequested = onReorderCategoriesRequested,
            onToggleCategoryVisibility = {
                val category = selectedCategory
                if (category != null && !categoryMutationInFlight) {
                    val previewCategoryKey = preview?.request?.channelId?.let { channelId ->
                        state.channelCategoryKeyById[channelId]
                    }
                    categoryMutationInFlight = true
                    categoryMutationError = null
                    mutationScope.launch {
                        try {
                            val result = if (category.isHidden) {
                                runtime.unhideCategory(sourceId, category.providerCategoryKey)
                            } else {
                                runtime.hideCategory(sourceId, category.providerCategoryKey)
                            }
                            when (result) {
                                is CategoryVisibilityMutationResult.Success -> {
                                    if (result.hidden && previewCategoryKey == result.providerCategoryKey) {
                                        onPreviewClosed()
                                    }
                                }
                                is CategoryVisibilityMutationResult.Failure -> {
                                    categoryMutationError = landscapeCategoryFailureLabel(result)
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
            },
            modifier = Modifier
                .weight(0.23f)
                .fillMaxHeight(),
        )

        PaneDivider()

        LandscapeChannelPane(
            state = state,
            editState = editState,
            playingChannelId = preview?.request?.channelId,
            loadingChannels = loadingChannels,
            channelRefreshFailed = channelRefreshFailed,
            onSearchChange = browseSession::updateSearch,
            onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
            onHiddenOnlyChanged = { enabled ->
                browseSession.setHiddenOnly(
                    enabled = enabled,
                    includeHiddenWhenDisabled = editState.isEditing,
                )
            },
            onOrderChanged = browseSession::setOrder,
            onCustomGroupSelected = browseSession::selectCustomGroup,
            onEditModeChanged = ::setEditing,
            onChannelSelected = ::selectChannel,
            onSelectionToggle = { channelId ->
                onEditStateChange(ChannelEditReducer.toggleSelection(editState, channelId))
            },
            onSelectVisible = {
                onEditStateChange(
                    ChannelEditReducer.selectVisible(
                        state = editState,
                        visibleChannelIds = state.channels.map { it.channelId },
                    ),
                )
            },
            onClearSelection = {
                onEditStateChange(ChannelEditReducer.clearSelection(editState))
            },
            onBulkAction = ::runBulkAction,
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
            onRetryChannels = { mutationScope.launch { runtime.refreshSource(sourceId) } },
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .weight(0.34f)
                .fillMaxHeight(),
        )

        PaneDivider()

        LandscapePlaybackEpgPane(
            preview = preview,
            playbackState = playbackState,
            videoOutput = videoOutput,
            epgSnapshot = epgSnapshot,
            epgLoading = epgLoading,
            epgFailed = epgFailed,
            onPlay = onPlay,
            onPause = onPause,
            onRetry = onRetry,
            onNavigatePreview = onNavigatePreview,
            onOpenFullscreen = onOpenFullscreen,
            onPreviewClosed = onPreviewClosed,
            onOpenEpgGuide = onOpenEpgGuide,
            modifier = Modifier
                .weight(0.43f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PaneDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun LandscapeCategoryPane(
    state: LiveBrowseState,
    editState: ChannelEditState,
    selectedCategory: LiveCategory?,
    categoryMutationInFlight: Boolean,
    categoryMutationError: String?,
    onCategorySelected: (String?) -> Unit,
    onReorderCategoriesRequested: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.categories.size} groups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editState.isEditing) {
                    TextButton(onClick = onReorderCategoriesRequested) { Text("Order") }
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "landscape-all-categories") {
                    LandscapeCategoryRow(
                        title = "All channels",
                        subtitle = "${state.catalogChannelCount} total",
                        selected = state.query.categoryKey == null,
                        hidden = false,
                        onClick = { onCategorySelected(null) },
                    )
                }
                items(
                    items = state.categories,
                    key = LiveCategory::providerCategoryKey,
                ) { category ->
                    LandscapeCategoryRow(
                        title = category.name,
                        subtitle = if (category.isHidden) "Hidden" else null,
                        selected = state.query.categoryKey == category.providerCategoryKey,
                        hidden = category.isHidden,
                        onClick = { onCategorySelected(category.providerCategoryKey) },
                    )
                }
            }

            AnimatedVisibility(visible = editState.isEditing && selectedCategory != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedCategory?.name.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (categoryMutationError != null) {
                        Text(
                            text = categoryMutationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = onToggleCategoryVisibility,
                        enabled = !categoryMutationInFlight,
                    ) {
                        Text(
                            if (selectedCategory?.isHidden == true) "Unhide category" else "Hide category",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeCategoryRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(MOTION_FAST),
        label = "categoryBackground",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hidden) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeChannelPane(
    state: LiveBrowseState,
    editState: ChannelEditState,
    playingChannelId: String?,
    loadingChannels: Boolean,
    channelRefreshFailed: Boolean,
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onHiddenOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onChannelSelected: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkAction: (ChannelBulkAction) -> Unit,
    onManualMoveRelative: (String, String, ManualOrderPlacement) -> Unit,
    onFavoriteMoveRelative: (String, String, ManualOrderPlacement) -> Unit,
    onRetryChannels: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    var draggedChannelId by remember { mutableStateOf<String?>(null) }
    var draggedPointerY by remember { mutableStateOf<Float?>(null) }
    var dragVisualOffsetY by remember { mutableFloatStateOf(0f) }
    var dragTarget by remember { mutableStateOf<ChannelDragTarget?>(null) }
    val manualDragEnabled = editState.isEditing && state.query.order == LiveBrowseOrder.MY_ORDER
    val favoriteDragEnabled = editState.isEditing &&
        state.query.favoritesOnly &&
        state.query.order == LiveBrowseOrder.FAVORITE_ORDER
    val dragEnabled = manualDragEnabled || favoriteDragEnabled

    fun clearDrag() {
        draggedChannelId = null
        draggedPointerY = null
        dragVisualOffsetY = 0f
        dragTarget = null
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = editState.isEditing,
                    transitionSpec = {
                        fadeIn(tween(MOTION_FAST)) togetherWith fadeOut(tween(MOTION_FAST))
                    },
                    label = "channelCountMode",
                    modifier = Modifier.weight(1f),
                ) { editing ->
                    Text(
                        text = if (editing) {
                            "${editState.selectedChannelIds.size} selected"
                        } else {
                            "${state.channels.size} channels"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LandscapeOrderMenu(state.query.order, onOrderChanged)
                TextButton(
                    onClick = {
                        if (searchExpanded || state.query.searchTerm.isNotBlank()) {
                            onSearchChange("")
                            searchExpanded = false
                        } else {
                            searchExpanded = true
                        }
                    },
                    enabled = !editState.isEditing,
                ) { Text(if (searchExpanded) "Close" else "Search") }
                TextButton(
                    onClick = {
                        clearDrag()
                        onEditModeChanged(!editState.isEditing)
                    },
                ) { Text(if (editState.isEditing) "Done" else "Edit") }
            }

            AnimatedVisibility(
                visible = searchExpanded || state.query.searchTerm.isNotBlank(),
                enter = fadeIn(tween(MOTION_MEDIUM)) + scaleIn(tween(MOTION_MEDIUM), initialScale = 0.98f),
                exit = fadeOut(tween(MOTION_FAST)) + scaleOut(tween(MOTION_FAST), targetScale = 0.98f),
            ) {
                OutlinedTextField(
                    value = state.query.searchTerm,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Search channels") },
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.query.favoritesOnly,
                    onClick = { onFavoritesOnlyChanged(!state.query.favoritesOnly) },
                    label = { Text("Favorites") },
                )
                FilterChip(
                    selected = state.query.hiddenOnly,
                    onClick = { onHiddenOnlyChanged(!state.query.hiddenOnly) },
                    label = { Text("Hidden") },
                )
                if (state.customGroups.isNotEmpty()) {
                    LandscapeGroupMenu(
                        groups = state.customGroups.map { it.groupId to it.name },
                        selectedGroupId = state.query.customGroupId,
                        onSelected = onCustomGroupSelected,
                    )
                }
            }

            AnimatedVisibility(visible = editState.isEditing) {
                LandscapeBulkEditBar(
                    selectedCount = editState.selectedChannelIds.size,
                    dragEnabled = dragEnabled,
                    favoriteDragEnabled = favoriteDragEnabled,
                    onSelectVisible = onSelectVisible,
                    onClearSelection = onClearSelection,
                    onBulkAction = onBulkAction,
                )
            }

            when {
                loadingChannels -> LandscapeInlineStatus(
                    if (state.catalogChannelCount == 0) "Loading channels…" else "Updating channels…",
                )
                channelRefreshFailed -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Refresh failed; existing channels kept.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetryChannels) { Text("Retry") }
                }
            }

            HorizontalDivider()

            if (state.catalogChannelCount == 0 && !loadingChannels) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("0 channels", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "No Live channels are loaded for this playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = onRetryChannels) { Text("Retry") }
                        TextButton(onClick = onOpenSettings) { Text("Settings") }
                    }
                }
            } else if (state.channels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No matching channels", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Change category, search, or filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(
                        items = state.channels,
                        key = { _, channel -> channel.channelId },
                    ) { index, channel ->
                        val isDragging = draggedChannelId == channel.channelId
                        val isDropAnchor = dragTarget?.anchorChannelId == channel.channelId
                        val handleModifier = if (dragEnabled) {
                            Modifier.pointerInput(channel.channelId, favoriteDragEnabled) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val itemInfo = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == channel.channelId }
                                        draggedChannelId = channel.channelId
                                        dragVisualOffsetY = 0f
                                        draggedPointerY = itemInfo?.let {
                                            it.offset + (it.size / 2f)
                                        }
                                        dragTarget = draggedPointerY?.let { pointerY ->
                                            resolveLandscapeDragTarget(
                                                pointerY = pointerY,
                                                draggedChannelId = channel.channelId,
                                                visibleItems = listState.layoutInfo.visibleItemsInfo,
                                            )
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val draggedId = draggedChannelId
                                            ?: return@detectDragGesturesAfterLongPress
                                        val nextY = (draggedPointerY
                                            ?: return@detectDragGesturesAfterLongPress) + dragAmount.y
                                        draggedPointerY = nextY
                                        dragVisualOffsetY += dragAmount.y
                                        val layout = listState.layoutInfo
                                        val edge = 68f
                                        val scrollDelta = when {
                                            nextY < layout.viewportStartOffset + edge -> -34f
                                            nextY > layout.viewportEndOffset - edge -> 34f
                                            else -> 0f
                                        }
                                        if (scrollDelta != 0f) {
                                            dragScope.launch {
                                                val consumed = listState.scrollBy(scrollDelta)
                                                dragVisualOffsetY += consumed
                                            }
                                        }
                                        dragTarget = resolveLandscapeDragTarget(
                                            pointerY = nextY,
                                            draggedChannelId = draggedId,
                                            visibleItems = layout.visibleItemsInfo,
                                        )
                                    },
                                    onDragEnd = {
                                        val dragged = draggedChannelId
                                        val target = dragTarget
                                        if (dragged != null && target != null) {
                                            if (favoriteDragEnabled) {
                                                onFavoriteMoveRelative(
                                                    dragged,
                                                    target.anchorChannelId,
                                                    target.placement,
                                                )
                                            } else {
                                                onManualMoveRelative(
                                                    dragged,
                                                    target.anchorChannelId,
                                                    target.placement,
                                                )
                                            }
                                        }
                                        clearDrag()
                                    },
                                    onDragCancel = ::clearDrag,
                                )
                            }
                        } else {
                            Modifier
                        }

                        LandscapeChannelRow(
                            channel = channel,
                            isEditing = editState.isEditing,
                            selected = channel.channelId in editState.selectedChannelIds,
                            playing = channel.channelId == playingChannelId,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragVisualOffsetY else 0f,
                            dropPlacement = if (isDropAnchor) dragTarget?.placement else null,
                            dragEnabled = dragEnabled,
                            dragHandleModifier = handleModifier,
                            onClick = { onChannelSelected(channel.channelId) },
                            onSelectionToggle = { onSelectionToggle(channel.channelId) },
                        )
                        if (index != state.channels.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 54.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeBulkEditBar(
    selectedCount: Int,
    dragEnabled: Boolean,
    favoriteDragEnabled: Boolean,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkAction: (ChannelBulkAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (dragEnabled) {
                    if (favoriteDragEnabled) "Drag ≡ · Favorite order" else "Drag ≡ · My Order"
                } else {
                    "$selectedCount selected"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSelectVisible) { Text("All") }
            TextButton(onClick = onClearSelection, enabled = selectedCount > 0) { Text("Clear") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(
                onClick = { onBulkAction(ChannelBulkAction.Hide) },
                enabled = selectedCount > 0,
            ) { Text("Hide") }
            TextButton(
                onClick = { onBulkAction(ChannelBulkAction.Unhide) },
                enabled = selectedCount > 0,
            ) { Text("Unhide") }
            TextButton(
                onClick = { onBulkAction(ChannelBulkAction.Favorite) },
                enabled = selectedCount > 0,
            ) { Text("Favorite") }
            TextButton(
                onClick = { onBulkAction(ChannelBulkAction.RemoveFavorite) },
                enabled = selectedCount > 0,
            ) { Text("Unfavorite") }
        }
    }
}

@Composable
private fun LandscapeChannelRow(
    channel: LiveChannelItem,
    isEditing: Boolean,
    selected: Boolean,
    playing: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    dropPlacement: ManualOrderPlacement?,
    dragEnabled: Boolean,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    val targetColor = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        dropPlacement != null -> MaterialTheme.colorScheme.surfaceVariant
        playing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.background
    }
    val background by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(MOTION_FAST),
        label = "channelBackground",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (dropPlacement == ManualOrderPlacement.BEFORE) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(3f),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 2f else 0f)
                .graphicsLayer {
                    translationY = dragOffsetY
                    scaleX = if (isDragging) 1.018f else 1f
                    scaleY = if (isDragging) 1.018f else 1f
                    alpha = if (isDragging) 0.98f else 1f
                }
                .background(background)
                .clickable {
                    if (isEditing) onSelectionToggle() else onClick()
                }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (isEditing) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionToggle() },
                )
            }
            if (dragEnabled) {
                Text(
                    text = "≡",
                    modifier = dragHandleModifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channel.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = buildList {
                    channel.categoryName?.takeIf(String::isNotBlank)?.let(::add)
                    if (channel.isFavorite) add("Favorite")
                    if (channel.isHidden) add("Hidden")
                }.joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(visible = playing) {
                Text(
                    text = "▶",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (dropPlacement == ManualOrderPlacement.AFTER) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(3f),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun resolveLandscapeDragTarget(
    pointerY: Float,
    draggedChannelId: String,
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
): ChannelDragTarget? = ChannelDragTargetResolver.resolve(
    pointerY = pointerY,
    draggedChannelId = draggedChannelId,
    visibleItems = visibleItems.mapNotNull { item ->
        val channelId = item.key as? String ?: return@mapNotNull null
        VisibleChannelBounds(
            channelId = channelId,
            top = item.offset.toFloat(),
            bottom = (item.offset + item.size).toFloat(),
        )
    },
)

@Composable
private fun LandscapePlaybackEpgPane(
    preview: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    epgSnapshot: EpgSnapshot?,
    epgLoading: Boolean,
    epgFailed: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onNavigatePreview: (PlaybackNavigationDirection) -> Unit,
    onOpenFullscreen: (LivePlaybackSelection) -> Unit,
    onPreviewClosed: () -> Unit,
    onOpenEpgGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (preview != null) {
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
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = 0.98f },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Live preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Select a channel from the middle column.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            AnimatedContent(
                targetState = preview?.request?.channelId,
                transitionSpec = {
                    (fadeIn(tween(MOTION_MEDIUM)) + scaleIn(tween(MOTION_MEDIUM), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(MOTION_FAST)) + scaleOut(tween(MOTION_FAST), targetScale = 0.99f))
                },
                label = "epgChannel",
                modifier = Modifier.weight(1f),
            ) { channelId ->
                if (channelId == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "EPG will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LandscapeEpgTimeline(
                        snapshot = epgSnapshot,
                        loading = epgLoading,
                        failed = epgFailed,
                        onOpenGuide = onOpenEpgGuide,
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeEpgTimeline(
    snapshot: EpgSnapshot?,
    loading: Boolean,
    failed: Boolean,
    onOpenGuide: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EPG",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (snapshot?.programs?.isNotEmpty() == true && !loading) {
                TextButton(onClick = onOpenGuide) { Text("Full guide") }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Updating EPG…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("EPG unavailable", color = MaterialTheme.colorScheme.error)
            }
            snapshot == null || snapshot.programs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No EPG for this channel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val visiblePrograms = landscapePrograms(snapshot)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                ) {
                    items(
                        items = visiblePrograms,
                        key = { program ->
                            "${program.startEpochSeconds}:${program.endEpochSeconds}:${program.title}"
                        },
                    ) { program ->
                        LandscapeProgramRow(
                            program = program,
                            current = program == snapshot.current,
                            next = program == snapshot.next,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun landscapePrograms(snapshot: EpgSnapshot): List<EpgProgram> {
    val programs = snapshot.programs
    val current = snapshot.current
    val currentIndex = current?.let(programs::indexOf) ?: -1
    return if (currentIndex >= 0) {
        programs.drop(currentIndex).take(10)
    } else {
        programs.take(10)
    }
}

@Composable
private fun LandscapeProgramRow(
    program: EpgProgram,
    current: Boolean,
    next: Boolean,
) {
    val background by animateColorAsState(
        targetValue = if (current) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(MOTION_FAST),
        label = "epgProgramBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = landscapeTimeRange(program),
            style = MaterialTheme.typography.labelMedium,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = when {
                current -> "NOW"
                next -> "NEXT"
                else -> null
            }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            if (current) {
                program.description?.takeIf(String::isNotBlank)?.let { description ->
                    Spacer(Modifier.height(2.dp))
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
private fun LandscapeOrderMenu(
    selected: LiveBrowseOrder,
    onSelected: (LiveBrowseOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(landscapeOrderLabel(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LiveBrowseOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(landscapeOrderLabel(order)) },
                    onClick = {
                        expanded = false
                        onSelected(order)
                    },
                )
            }
        }
    }
}

@Composable
private fun LandscapeGroupMenu(
    groups: List<Pair<String, String>>,
    selectedGroupId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = groups.firstOrNull { it.first == selectedGroupId }?.second ?: "Groups"
    Box {
        FilterChip(
            selected = selectedGroupId != null,
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All groups") },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            groups.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelected(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun LandscapeInlineStatus(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun landscapeOrderLabel(order: LiveBrowseOrder): String = when (order) {
    LiveBrowseOrder.PROVIDER -> "Provider"
    LiveBrowseOrder.MY_ORDER -> "My Order"
    LiveBrowseOrder.FAVORITE_ORDER -> "Favorite order"
    LiveBrowseOrder.RECENTLY_WATCHED -> "Recent"
    LiveBrowseOrder.A_TO_Z -> "A–Z"
    LiveBrowseOrder.Z_TO_A -> "Z–A"
    LiveBrowseOrder.CATEGORY -> "Category"
}

private fun landscapeCategoryFailureLabel(
    failure: CategoryVisibilityMutationResult.Failure,
): String = when (failure.reason) {
    CategoryVisibilityFailureReason.CATEGORY_NOT_FOUND -> "Category is no longer available."
    CategoryVisibilityFailureReason.PERSISTENCE_FAILURE -> "Could not save category visibility."
    CategoryVisibilityFailureReason.INVALID_SOURCE_ID,
    CategoryVisibilityFailureReason.EMPTY_CATEGORY_KEY,
    -> "Category visibility is unavailable."
}

private fun landscapeTimeRange(program: EpgProgram): String = when {
    program.startLabel != null && program.endLabel != null ->
        "${program.startLabel}–${program.endLabel}"
    program.startLabel != null -> program.startLabel
    else -> "—"
}
