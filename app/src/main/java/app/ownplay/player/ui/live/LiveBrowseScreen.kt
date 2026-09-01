package app.ownplay.player.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.live.LiveCustomGroup
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelDragTarget
import app.ownplay.player.personalization.ChannelDragTargetResolver
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.personalization.ManualOrderPlacement
import app.ownplay.player.personalization.VisibleChannelBounds
import app.ownplay.player.ui.DragAutoScrollPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHANNEL_DRAG_EDGE_PX = 80f
private const val CHANNEL_DRAG_SCROLL_STEP_PX = 36f
private const val CHANNEL_DRAG_SCROLL_FRAME_MILLIS = 16L

@Composable
fun LiveBrowseScreen(
    state: LiveBrowseState,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onChannelSelected: (String) -> Unit,
    onCustomGroupSelected: (String?) -> Unit = {},
    onHiddenOnlyChanged: (Boolean) -> Unit = {},
    editState: ChannelEditState = ChannelEditState(),
    playingChannelId: String? = null,
    onEditModeChanged: (Boolean) -> Unit = {},
    onReorderCategoriesRequested: () -> Unit = {},
    onChannelSelectionToggle: (String) -> Unit = {},
    onSelectVisible: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBulkAction: (ChannelBulkAction) -> Unit = {},
    onCreateGroup: (String) -> Unit = {},
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onDeleteGroup: (String) -> Unit = {},
    onSetLocalDisplayName: (String, String) -> Unit = { _, _ -> },
    onClearLocalDisplayName: (String) -> Unit = {},
    onSetLogoOverride: (String, String) -> Unit = { _, _ -> },
    onClearLogoOverride: (String) -> Unit = {},
    onManualMoveRelative: (String, String, ManualOrderPlacement) -> Unit = { _, _, _ -> },
    onFavoriteMoveRelative: (String, String, ManualOrderPlacement) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    val validChannelIds = remember(state.channels) {
        state.channels.mapTo(hashSetOf()) { channel -> channel.channelId }
    }
    var draggedChannelId by remember { mutableStateOf<String?>(null) }
    var draggedPointerY by remember { mutableStateOf<Float?>(null) }
    var dragVisualOffsetY by remember { mutableFloatStateOf(0f) }
    var dragTarget by remember { mutableStateOf<ChannelDragTarget?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val manualDragEnabled = editState.isEditing && state.query.order == LiveBrowseOrder.MY_ORDER
    val favoriteDragEnabled = editState.isEditing &&
        state.query.favoritesOnly &&
        state.query.order == LiveBrowseOrder.FAVORITE_ORDER
    val dragEnabled = manualDragEnabled || favoriteDragEnabled

    fun clearDragState() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggedChannelId = null
        draggedPointerY = null
        dragVisualOffsetY = 0f
        dragTarget = null
    }

    fun refreshDragTarget() {
        val draggedId = draggedChannelId ?: return
        val pointerY = draggedPointerY ?: return
        dragTarget = resolveDragTarget(
            pointerY = pointerY,
            draggedChannelId = draggedId,
            visibleItems = listState.layoutInfo.visibleItemsInfo,
            validChannelIds = validChannelIds,
        )
    }

    fun updateAutoScroll() {
        val draggedId = draggedChannelId
        val pointerY = draggedPointerY
        if (draggedId == null || pointerY == null) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }

        val layout = listState.layoutInfo
        val initialDelta = DragAutoScrollPolicy.delta(
            pointerY = pointerY,
            viewportStart = layout.viewportStartOffset,
            viewportEnd = layout.viewportEndOffset,
            edgeSize = CHANNEL_DRAG_EDGE_PX,
            step = CHANNEL_DRAG_SCROLL_STEP_PX,
        )
        if (initialDelta == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = dragScope.launch {
            while (draggedChannelId == draggedId) {
                val currentPointerY = draggedPointerY ?: break
                val currentLayout = listState.layoutInfo
                val scrollDelta = DragAutoScrollPolicy.delta(
                    pointerY = currentPointerY,
                    viewportStart = currentLayout.viewportStartOffset,
                    viewportEnd = currentLayout.viewportEndOffset,
                    edgeSize = CHANNEL_DRAG_EDGE_PX,
                    step = CHANNEL_DRAG_SCROLL_STEP_PX,
                )
                if (scrollDelta == 0f) break

                val consumed = listState.scrollBy(scrollDelta)
                if (consumed == 0f) break
                dragVisualOffsetY += consumed
                refreshDragTarget()
                delay(CHANNEL_DRAG_SCROLL_FRAME_MILLIS)
            }
            autoScrollJob = null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "browse-header") {
                LiveBrowseHeader(
                    state = state,
                    editState = editState,
                    onSearchChange = onSearchChange,
                    onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                    onHiddenOnlyChanged = onHiddenOnlyChanged,
                    onOrderChanged = onOrderChanged,
                    onReorderCategoriesRequested = onReorderCategoriesRequested,
                    onEditModeChanged = { editing ->
                        val editOrder = if (state.query.favoritesOnly) {
                            LiveBrowseOrder.FAVORITE_ORDER
                        } else {
                            LiveBrowseOrder.MY_ORDER
                        }
                        if (editing && state.query.order != editOrder) {
                            onOrderChanged(editOrder)
                        }
                        if (!editing) clearDragState()
                        onEditModeChanged(editing)
                    },
                )
            }

            item(key = "category-strip") {
                CategoryStrip(
                    categories = state.categories,
                    selectedCategoryKey = state.query.categoryKey,
                    onCategorySelected = onCategorySelected,
                )
            }

            if (state.customGroups.isNotEmpty()) {
                item(key = "custom-groups") {
                    CustomGroupStrip(
                        groups = state.customGroups,
                        selectedGroupId = state.query.customGroupId,
                        onGroupSelected = onCustomGroupSelected,
                    )
                }
            }

            if (editState.isEditing) {
                item(key = "bulk-edit") {
                    val selectedVisibleChannel = state.channels.singleOrNull { channel ->
                        channel.channelId in editState.selectedChannelIds
                    }
                    BulkEditBar(
                        selectedCount = editState.selectedChannelIds.size,
                        selectedVisibleChannel = selectedVisibleChannel,
                        groups = state.customGroups,
                        dragEnabled = dragEnabled,
                        favoriteDragEnabled = favoriteDragEnabled,
                        onSelectVisible = onSelectVisible,
                        onClearSelection = onClearSelection,
                        onBulkAction = onBulkAction,
                        onCreateGroup = onCreateGroup,
                        onRenameGroup = onRenameGroup,
                        onDeleteGroup = onDeleteGroup,
                        onSetLocalDisplayName = onSetLocalDisplayName,
                        onClearLocalDisplayName = onClearLocalDisplayName,
                        onSetLogoOverride = onSetLogoOverride,
                        onClearLogoOverride = onClearLogoOverride,
                    )
                }
            }

            item(key = "channel-divider") {
                HorizontalDivider()
            }

            if (state.channels.isEmpty()) {
                item(key = "empty") {
                    LiveEmptyState(
                        hasActiveFilter = state.query.searchTerm.isNotBlank() ||
                            state.query.categoryKey != null ||
                            state.query.customGroupId != null ||
                            state.query.favoritesOnly ||
                            state.query.hiddenOnly,
                    )
                }
            } else {
                itemsIndexed(
                    items = state.channels,
                    key = { _, channel -> channel.channelId },
                ) { index, channel ->
                    val isDropAnchor = dragTarget?.anchorChannelId == channel.channelId
                    val dragHandleModifier = if (dragEnabled) {
                        Modifier.pointerInput(channel.channelId, favoriteDragEnabled) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info -> info.key == channel.channelId }
                                    draggedChannelId = channel.channelId
                                    dragVisualOffsetY = 0f
                                    draggedPointerY = itemInfo?.let { info ->
                                        info.offset + (info.size / 2f)
                                    }
                                    refreshDragTarget()
                                    updateAutoScroll()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggedChannelId == null) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    draggedPointerY = (draggedPointerY
                                        ?: return@detectDragGesturesAfterLongPress) + dragAmount.y
                                    dragVisualOffsetY += dragAmount.y
                                    refreshDragTarget()
                                    updateAutoScroll()
                                },
                                onDragEnd = {
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                    refreshDragTarget()
                                    val draggedId = draggedChannelId
                                    val target = dragTarget
                                    if (draggedId != null && target != null) {
                                        if (favoriteDragEnabled) {
                                            onFavoriteMoveRelative(
                                                draggedId,
                                                target.anchorChannelId,
                                                target.placement,
                                            )
                                        } else {
                                            onManualMoveRelative(
                                                draggedId,
                                                target.anchorChannelId,
                                                target.placement,
                                            )
                                        }
                                    }
                                    clearDragState()
                                },
                                onDragCancel = ::clearDragState,
                            )
                        }
                    } else {
                        Modifier
                    }

                    LiveChannelRow(
                        channel = channel,
                        isEditing = editState.isEditing,
                        isSelected = channel.channelId in editState.selectedChannelIds,
                        isPlaying = channel.channelId == playingChannelId,
                        isDragging = draggedChannelId == channel.channelId,
                        dragVisualOffsetY = dragVisualOffsetY,
                        dropPlacement = if (isDropAnchor) dragTarget?.placement else null,
                        showDragHandle = dragEnabled,
                        dragHandleModifier = dragHandleModifier,
                        onClick = { onChannelSelected(channel.channelId) },
                        onSelectionToggle = { onChannelSelectionToggle(channel.channelId) },
                    )
                    if (index != state.channels.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = if (editState.isEditing) 116.dp else 68.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun resolveDragTarget(
    pointerY: Float,
    draggedChannelId: String,
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
    validChannelIds: Set<String>,
): ChannelDragTarget? = ChannelDragTargetResolver.resolve(
    pointerY = pointerY,
    draggedChannelId = draggedChannelId,
    visibleItems = visibleItems.mapNotNull { item ->
        val channelId = item.key as? String ?: return@mapNotNull null
        if (channelId !in validChannelIds) return@mapNotNull null
        VisibleChannelBounds(
            channelId = channelId,
            top = item.offset.toFloat(),
            bottom = (item.offset + item.size).toFloat(),
        )
    },
    validChannelIds = validChannelIds,
)

@Composable
private fun LiveBrowseHeader(
    state: LiveBrowseState,
    editState: ChannelEditState,
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onHiddenOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onReorderCategoriesRequested: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val showSearch = searchExpanded || state.query.searchTerm.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editState.isEditing) {
                    "${editState.selectedChannelIds.size} selected"
                } else {
                    "${state.channels.size} channels"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editState.isEditing) {
                TextButton(onClick = onReorderCategoriesRequested) {
                    Text("Categories")
                }
            } else {
                LiveOrderMenu(
                    selected = state.query.order,
                    onSelected = onOrderChanged,
                )
                TextButton(
                    onClick = {
                        if (showSearch) {
                            onSearchChange("")
                            searchExpanded = false
                        } else {
                            searchExpanded = true
                        }
                    },
                ) {
                    Text(if (showSearch) "Close search" else "Search")
                }
            }
            TextButton(onClick = { onEditModeChanged(!editState.isEditing) }) {
                Text(if (editState.isEditing) "Done" else "Edit")
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = state.query.searchTerm,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search channels") },
                shape = RoundedCornerShape(12.dp),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "favorites-filter") {
                FilterChip(
                    selected = state.query.favoritesOnly,
                    onClick = { onFavoritesOnlyChanged(!state.query.favoritesOnly) },
                    label = { Text("Favorites") },
                )
            }
            item(key = "hidden-filter") {
                FilterChip(
                    selected = state.query.hiddenOnly,
                    onClick = { onHiddenOnlyChanged(!state.query.hiddenOnly) },
                    label = { Text("Hidden") },
                )
            }
        }
    }
}

@Composable
private fun CategoryStrip(
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedCategoryKey == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
            )
        }
        items(
            items = categories,
            key = LiveCategory::providerCategoryKey,
        ) { category ->
            FilterChip(
                selected = selectedCategoryKey == category.providerCategoryKey,
                onClick = { onCategorySelected(category.providerCategoryKey) },
                label = {
                    Text(
                        text = if (category.isHidden) "${category.name} · hidden" else category.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun CustomGroupStrip(
    groups: List<LiveCustomGroup>,
    selectedGroupId: String?,
    onGroupSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 6.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all-groups") {
            FilterChip(
                selected = selectedGroupId == null,
                onClick = { onGroupSelected(null) },
                label = { Text("All groups") },
            )
        }
        items(
            items = groups,
            key = LiveCustomGroup::groupId,
        ) { group ->
            FilterChip(
                selected = selectedGroupId == group.groupId,
                onClick = { onGroupSelected(group.groupId) },
                label = {
                    Text(
                        text = group.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun BulkEditBar(
    selectedCount: Int,
    selectedVisibleChannel: LiveChannelItem?,
    groups: List<LiveCustomGroup>,
    dragEnabled: Boolean,
    favoriteDragEnabled: Boolean,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkAction: (ChannelBulkAction) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onSetLocalDisplayName: (String, String) -> Unit,
    onClearLocalDisplayName: (String) -> Unit,
    onSetLogoOverride: (String, String) -> Unit,
    onClearLogoOverride: (String) -> Unit,
) {
    val hasSelection = selectedCount > 0
    var showGroupManager by remember { mutableStateOf(false) }
    var customizeTarget by remember { mutableStateOf<LiveChannelItem?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSelectVisible) {
                Text("Select visible")
            }
            TextButton(
                onClick = onClearSelection,
                enabled = hasSelection,
            ) {
                Text("Clear")
            }
        }
        if (dragEnabled) {
            Text(
                text = if (favoriteDragEnabled) {
                    "Drag the ≡ handle to reorder Favorite order."
                } else {
                    "Drag the ≡ handle to reorder My Order."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "hide") {
                TextButton(
                    onClick = { onBulkAction(ChannelBulkAction.Hide) },
                    enabled = hasSelection,
                ) {
                    Text("Hide")
                }
            }
            item(key = "unhide") {
                TextButton(
                    onClick = { onBulkAction(ChannelBulkAction.Unhide) },
                    enabled = hasSelection,
                ) {
                    Text("Unhide")
                }
            }
            item(key = "favorite") {
                TextButton(
                    onClick = { onBulkAction(ChannelBulkAction.Favorite) },
                    enabled = hasSelection,
                ) {
                    Text("Favorite")
                }
            }
            item(key = "remove-favorite") {
                TextButton(
                    onClick = { onBulkAction(ChannelBulkAction.RemoveFavorite) },
                    enabled = hasSelection,
                ) {
                    Text("Unfavorite")
                }
            }
            item(key = "move-top") {
                TextButton(
                    onClick = {
                        onBulkAction(
                            if (favoriteDragEnabled) {
                                ChannelBulkAction.MoveFavoritesToTop
                            } else {
                                ChannelBulkAction.MoveToTop
                            },
                        )
                    },
                    enabled = hasSelection,
                ) {
                    Text(if (favoriteDragEnabled) "Favorite top" else "Move top")
                }
            }
            item(key = "move-bottom") {
                TextButton(
                    onClick = {
                        onBulkAction(
                            if (favoriteDragEnabled) {
                                ChannelBulkAction.MoveFavoritesToBottom
                            } else {
                                ChannelBulkAction.MoveToBottom
                            },
                        )
                    },
                    enabled = hasSelection,
                ) {
                    Text(if (favoriteDragEnabled) "Favorite bottom" else "Move bottom")
                }
            }
            item(key = "customize-channel") {
                TextButton(
                    onClick = { customizeTarget = selectedVisibleChannel },
                    enabled = selectedCount == 1 && selectedVisibleChannel != null,
                ) {
                    Text("Customize")
                }
            }
            item(key = "manage-groups") {
                TextButton(onClick = { showGroupManager = true }) {
                    Text("Groups")
                }
            }
            if (groups.isNotEmpty()) {
                item(key = "add-group") {
                    GroupActionMenu(
                        label = "Add to group",
                        groups = groups,
                        enabled = hasSelection,
                        onGroupSelected = { groupId ->
                            onBulkAction(ChannelBulkAction.AddToGroup(groupId))
                        },
                    )
                }
                item(key = "remove-group") {
                    GroupActionMenu(
                        label = "Remove group",
                        groups = groups,
                        enabled = hasSelection,
                        onGroupSelected = { groupId ->
                            onBulkAction(ChannelBulkAction.RemoveFromGroup(groupId))
                        },
                    )
                }
            }
        }
    }

    customizeTarget?.let { channel ->
        ChannelCustomizationDialog(
            channel = channel,
            onSetLocalDisplayName = onSetLocalDisplayName,
            onClearLocalDisplayName = onClearLocalDisplayName,
            onSetLogoOverride = onSetLogoOverride,
            onClearLogoOverride = onClearLogoOverride,
            onDismiss = { customizeTarget = null },
        )
    }

    if (showGroupManager) {
        CustomGroupManagerDialog(
            groups = groups,
            onCreateGroup = onCreateGroup,
            onRenameGroup = onRenameGroup,
            onDeleteGroup = onDeleteGroup,
            onDismiss = { showGroupManager = false },
        )
    }
}

@Composable
private fun GroupActionMenu(
    label: String,
    groups: List<LiveCustomGroup>,
    enabled: Boolean,
    onGroupSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        expanded = false
                        onGroupSelected(group.groupId)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: LiveChannelItem,
    isEditing: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    dragVisualOffsetY: Float,
    dropPlacement: ManualOrderPlacement?,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    val highlight = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        dropPlacement != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
        else -> MaterialTheme.colorScheme.background
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (dropPlacement == ManualOrderPlacement.BEFORE) {
            ChannelInsertionIndicator(modifier = Modifier.align(Alignment.TopCenter))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 2f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragVisualOffsetY else 0f
                    scaleX = if (isDragging) 1.015f else 1f
                    scaleY = if (isDragging) 1.015f else 1f
                    alpha = if (isDragging) 0.98f else 1f
                }
                .background(highlight)
                .clickable {
                    if (isEditing) onSelectionToggle() else onClick()
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isEditing) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle() },
                )
            }

            if (showDragHandle) {
                Text(
                    text = "≡",
                    modifier = dragHandleModifier
                        .background(
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channel.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = channel.categoryName?.takeIf(String::isNotBlank)
                if (secondary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (isPlaying) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (channel.isFavorite) {
                    Text(
                        text = "Favorite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (channel.isHidden) {
                    Text(
                        text = "Hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (dropPlacement == ManualOrderPlacement.AFTER) {
            ChannelInsertionIndicator(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ChannelInsertionIndicator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .zIndex(3f),
        thickness = 3.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LiveEmptyState(hasActiveFilter: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (hasActiveFilter) "No matching channels" else "No live channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (hasActiveFilter) {
                    "Change the search or filters to see more channels."
                } else {
                    "Channels from your configured media source will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveOrderMenu(
    selected: LiveBrowseOrder,
    onSelected: (LiveBrowseOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(orderLabel(selected))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LiveBrowseOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(orderLabel(order)) },
                    onClick = {
                        expanded = false
                        onSelected(order)
                    },
                )
            }
        }
    }
}

private fun orderLabel(order: LiveBrowseOrder): String = when (order) {
    LiveBrowseOrder.PROVIDER -> "Provider order"
    LiveBrowseOrder.MY_ORDER -> "My Order"
    LiveBrowseOrder.FAVORITE_ORDER -> "Favorite order"
    LiveBrowseOrder.RECENTLY_WATCHED -> "Recently watched"
    LiveBrowseOrder.A_TO_Z -> "A–Z"
    LiveBrowseOrder.Z_TO_A -> "Z–A"
    LiveBrowseOrder.CATEGORY -> "Category"
}
