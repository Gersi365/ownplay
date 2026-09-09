package app.ownplay.player.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem

/**
 * Dedicated remote-first Live browser for the TV source set.
 *
 * The TV surface intentionally avoids the portrait/touch toolbar, chips, text-field shell and
 * dropdown presentation. Live filtering/personalization state stays owned by LiveBrowseSession;
 * this component owns only the two-level categories -> channels presentation and focus continuity.
 */
@Composable
internal fun TvLiveChannelBrowser(
    state: LiveBrowseState,
    hierarchyLevel: LiveBrowseHierarchyLevel,
    playingChannelId: String?,
    onCategorySelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    focusChannelId: String?,
    focusRequestGeneration: Int,
    channelFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    when (hierarchyLevel) {
        LiveBrowseHierarchyLevel.CATEGORIES -> TvLiveCategoryList(
            state = state,
            onCategorySelected = onCategorySelected,
            modifier = modifier,
        )

        LiveBrowseHierarchyLevel.CHANNELS -> TvLiveChannelList(
            state = state,
            playingChannelId = playingChannelId,
            onChannelSelected = onChannelSelected,
            focusChannelId = focusChannelId,
            focusRequestGeneration = focusRequestGeneration,
            channelFocusRequester = channelFocusRequester,
            modifier = modifier,
        )
    }
}

@Composable
private fun TvLiveCategoryList(
    state: LiveBrowseState,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferredCategoryKey = state.query.categoryKey?.takeIf { selected ->
        state.categories.any { category -> category.providerCategoryKey == selected }
    } ?: state.categories.firstOrNull()?.providerCategoryKey
    val preferredCategoryIndex = remember(preferredCategoryKey, state.categories) {
        state.categories.indexOfFirst { category ->
            category.providerCategoryKey == preferredCategoryKey
        }
    }
    val listState = rememberLazyListState()
    val initialFocusRequester = remember(preferredCategoryKey, state.categories) { FocusRequester() }

    LaunchedEffect(preferredCategoryKey, preferredCategoryIndex, state.categories) {
        if (preferredCategoryKey == null || preferredCategoryIndex < 0) return@LaunchedEffect
        listState.scrollToItem(preferredCategoryIndex)
        withFrameNanos { }
        initialFocusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvLiveSectionHeader(
            title = "Live categories",
            subtitle = "Choose a category and press OK to browse its channels.",
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.categories,
                key = LiveCategory::providerCategoryKey,
            ) { category ->
                val preferred = category.providerCategoryKey == preferredCategoryKey
                TvLiveCategoryRow(
                    category = category,
                    onClick = { onCategorySelected(category.providerCategoryKey) },
                    modifier = if (preferred) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun TvLiveChannelList(
    state: LiveBrowseState,
    playingChannelId: String?,
    onChannelSelected: (String) -> Unit,
    focusChannelId: String?,
    focusRequestGeneration: Int,
    channelFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val resolvedFocusId = focusChannelId?.takeIf { candidate ->
        state.channels.any { channel -> channel.channelId == candidate }
    } ?: state.channels.firstOrNull()?.channelId
    val resolvedIndex = remember(resolvedFocusId, state.channels) {
        state.channels.indexOfFirst { channel -> channel.channelId == resolvedFocusId }
    }
    val listState = rememberLazyListState()
    val selectedCategoryName = state.query.categoryKey?.let { key ->
        state.categories.firstOrNull { category -> category.providerCategoryKey == key }?.name
    }

    LaunchedEffect(resolvedFocusId, focusRequestGeneration, state.channels) {
        if (resolvedFocusId == null || resolvedIndex < 0 || focusRequestGeneration <= 0) {
            return@LaunchedEffect
        }
        listState.scrollToItem(resolvedIndex)
        withFrameNanos { }
        channelFocusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvLiveSectionHeader(
            title = selectedCategoryName ?: "Live channels",
            subtitle = if (state.channels.isEmpty()) {
                "No channels are available in this category."
            } else {
                "${state.channels.size} channels · Up/Down to browse · OK to preview"
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.channels,
                key = LiveChannelItem::channelId,
            ) { channel ->
                val ownsFocusRequester = channel.channelId == resolvedFocusId
                TvLiveChannelRow(
                    channel = channel,
                    playing = channel.channelId == playingChannelId,
                    onClick = { onChannelSelected(channel.channelId) },
                    modifier = if (ownsFocusRequester) {
                        Modifier.focusRequester(channelFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun TvLiveSectionHeader(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvLiveCategoryRow(
    category: LiveCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(category.providerCategoryKey) { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(14.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.isHidden) {
                    Text(
                        text = "Hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TvLiveChannelRow(
    channel: LiveChannelItem,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(channel.channelId) { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(14.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer
            playing -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        playing -> "Previewing"
                        channel.isFavorite -> "Favorite"
                        else -> channel.categoryName.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (focused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
