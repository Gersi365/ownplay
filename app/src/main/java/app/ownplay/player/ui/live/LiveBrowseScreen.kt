package app.ownplay.player.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.live.LiveCustomGroup
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelEditState

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
    onEditModeChanged: (Boolean) -> Unit = {},
    onChannelSelectionToggle: (String) -> Unit = {},
    onSelectVisible: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBulkAction: (ChannelBulkAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            LiveBrowseHeader(
                state = state,
                editState = editState,
                onSearchChange = onSearchChange,
                onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                onHiddenOnlyChanged = onHiddenOnlyChanged,
                onOrderChanged = onOrderChanged,
                onEditModeChanged = onEditModeChanged,
            )
            CategoryStrip(
                categories = state.categories,
                selectedCategoryKey = state.query.categoryKey,
                onCategorySelected = onCategorySelected,
            )
            if (state.customGroups.isNotEmpty()) {
                CustomGroupStrip(
                    groups = state.customGroups,
                    selectedGroupId = state.query.customGroupId,
                    onGroupSelected = onCustomGroupSelected,
                )
            }
            if (editState.isEditing) {
                BulkEditBar(
                    selectedCount = editState.selectedChannelIds.size,
                    groups = state.customGroups,
                    onSelectVisible = onSelectVisible,
                    onClearSelection = onClearSelection,
                    onBulkAction = onBulkAction,
                )
            }
            HorizontalDivider()
            if (state.channels.isEmpty()) {
                LiveEmptyState(
                    hasActiveFilter = state.query.searchTerm.isNotBlank() ||
                        state.query.categoryKey != null ||
                        state.query.customGroupId != null ||
                        state.query.favoritesOnly ||
                        state.query.hiddenOnly,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = state.channels,
                        key = { _, channel -> channel.channelId },
                    ) { index, channel ->
                        LiveChannelRow(
                            channel = channel,
                            isEditing = editState.isEditing,
                            isSelected = channel.channelId in editState.selectedChannelIds,
                            onClick = { onChannelSelected(channel.channelId) },
                            onSelectionToggle = { onChannelSelectionToggle(channel.channelId) },
                        )
                        if (index != state.channels.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = if (editState.isEditing) 116.dp else 68.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBrowseHeader(
    state: LiveBrowseState,
    editState: ChannelEditState,
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onHiddenOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (editState.isEditing) {
                        "${editState.selectedChannelIds.size} selected"
                    } else {
                        "${state.channels.size} channels"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!editState.isEditing) {
                LiveOrderMenu(
                    selected = state.query.order,
                    onSelected = onOrderChanged,
                )
            }
            TextButton(onClick = { onEditModeChanged(!editState.isEditing) }) {
                Text(if (editState.isEditing) "Done" else "Edit")
            }
        }

        OutlinedTextField(
            value = state.query.searchTerm,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search channels") },
        )

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
            start = 20.dp,
            end = 20.dp,
            bottom = 8.dp,
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
                        text = category.name,
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
            start = 20.dp,
            end = 20.dp,
            bottom = 12.dp,
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
    groups: List<LiveCustomGroup>,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkAction: (ChannelBulkAction) -> Unit,
) {
    val hasSelection = selectedCount > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    onClick = { onBulkAction(ChannelBulkAction.MoveToTop) },
                    enabled = hasSelection,
                ) {
                    Text("Move top")
                }
            }
            item(key = "move-bottom") {
                TextButton(
                    onClick = { onBulkAction(ChannelBulkAction.MoveToBottom) },
                    enabled = hasSelection,
                ) {
                    Text("Move bottom")
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
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isEditing) onSelectionToggle() else onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isEditing) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
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
                fontWeight = FontWeight.Medium,
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
}

@Composable
private fun LiveEmptyState(hasActiveFilter: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
