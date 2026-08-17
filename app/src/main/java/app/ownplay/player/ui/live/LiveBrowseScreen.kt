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

@Composable
fun LiveBrowseScreen(
    state: LiveBrowseState,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onChannelSelected: (String) -> Unit,
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
                onSearchChange = onSearchChange,
                onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                onOrderChanged = onOrderChanged,
            )
            CategoryStrip(
                categories = state.categories,
                selectedCategoryKey = state.query.categoryKey,
                onCategorySelected = onCategorySelected,
            )
            HorizontalDivider()
            if (state.channels.isEmpty()) {
                LiveEmptyState(
                    hasActiveFilter = state.query.searchTerm.isNotBlank() ||
                        state.query.categoryKey != null ||
                        state.query.favoritesOnly,
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
                            onClick = { onChannelSelected(channel.channelId) },
                        )
                        if (index != state.channels.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp),
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
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
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
                    text = "${state.channels.size} channels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LiveOrderMenu(
                selected = state.query.order,
                onSelected = onOrderChanged,
            )
        }

        OutlinedTextField(
            value = state.query.searchTerm,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search channels") },
        )

        FilterChip(
            selected = state.query.favoritesOnly,
            onClick = { onFavoritesOnlyChanged(!state.query.favoritesOnly) },
            label = { Text("Favorites") },
        )
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
            bottom = 12.dp,
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
private fun LiveChannelRow(
    channel: LiveChannelItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        if (channel.isFavorite) {
            Text(
                text = "Favorite",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
    LiveBrowseOrder.A_TO_Z -> "A–Z"
    LiveBrowseOrder.Z_TO_A -> "Z–A"
    LiveBrowseOrder.CATEGORY -> "Category"
}
