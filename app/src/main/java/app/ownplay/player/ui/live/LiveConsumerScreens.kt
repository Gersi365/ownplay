package app.ownplay.player.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.epg.EpgTimelineProjector
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.ui.LivePreviewPanel

private const val LIVE_MOTION_FAST = 140

@Composable
internal fun PortraitLiveBrowse(
    state: LiveBrowseState,
    playingChannelId: String?,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val showSearch = searchExpanded || state.query.searchTerm.isNotBlank()

    Column(modifier = modifier.fillMaxSize()) {
        CompactLiveToolbar(
            state = state,
            showSearch = showSearch,
            onToggleSearch = {
                if (showSearch) {
                    onSearchChange("")
                    searchExpanded = false
                } else {
                    searchExpanded = true
                }
            },
            onFavoritesOnlyChanged = onFavoritesOnlyChanged,
            onOrderChanged = onOrderChanged,
            onCustomGroupSelected = onCustomGroupSelected,
        )

        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(LIVE_MOTION_FAST)),
            exit = fadeOut(tween(LIVE_MOTION_FAST)),
        ) {
            OutlinedTextField(
                value = state.query.searchTerm,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                singleLine = true,
                placeholder = { Text("Search channels") },
                shape = RoundedCornerShape(12.dp),
            )
        }

        CategoryStripCompact(
            categories = state.categories,
            selectedCategoryKey = state.query.categoryKey,
            onCategorySelected = onCategorySelected,
        )

        HorizontalDivider()

        ChannelListCompact(
            channels = state.channels,
            playingChannelId = playingChannelId,
            onChannelSelected = onChannelSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun LandscapeLiveWorkspaceSimple(
    state: LiveBrowseState,
    preview: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    epgSnapshot: EpgSnapshot?,
    epgLoading: Boolean,
    epgFailed: Boolean,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onNavigatePreview: (PlaybackNavigationDirection) -> Unit,
    onOpenFullscreen: (LivePlaybackSelection) -> Unit,
    onPreviewClosed: () -> Unit,
    onOpenEpgGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryColumn(
            state = state,
            onCategorySelected = onCategorySelected,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .weight(0.22f)
                .fillMaxHeight(),
        )

        PaneDivider()

        ChannelColumn(
            state = state,
            playingChannelId = preview?.request?.channelId,
            onSearchChange = onSearchChange,
            onFavoritesOnlyChanged = onFavoritesOnlyChanged,
            onOrderChanged = onOrderChanged,
            onCustomGroupSelected = onCustomGroupSelected,
            onChannelSelected = onChannelSelected,
            modifier = Modifier
                .weight(0.34f)
                .fillMaxHeight(),
        )

        PaneDivider()

        PlaybackAndEpgColumn(
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
                .weight(0.44f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun CompactLiveToolbar(
    state: LiveBrowseState,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${state.channels.size} channels",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompactAction(
            label = if (state.query.favoritesOnly) "★" else "☆",
            selected = state.query.favoritesOnly,
            onClick = { onFavoritesOnlyChanged(!state.query.favoritesOnly) },
        )
        CompactOrderMenu(
            selected = state.query.order,
            onSelected = onOrderChanged,
        )
        if (state.customGroups.isNotEmpty()) {
            CompactGroupMenu(
                groups = state.customGroups.map { it.groupId to it.name },
                selectedGroupId = state.query.customGroupId,
                onSelected = onCustomGroupSelected,
            )
        }
        CompactAction(
            label = if (showSearch) "×" else "⌕",
            selected = showSearch,
            onClick = onToggleSearch,
        )
    }
}

@Composable
private fun CompactAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(LIVE_MOTION_FAST),
        label = "compactActionColor",
    )
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = background,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CategoryStripCompact(
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "consumer-all") {
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
private fun CategoryColumn(
    state: LiveBrowseState,
    onCategorySelected: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Categories",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.categories.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "landscape-consumer-all") {
                    CategoryRowCompact(
                        title = "All channels",
                        countLabel = state.catalogChannelCount.toString(),
                        selected = state.query.categoryKey == null,
                        onClick = { onCategorySelected(null) },
                    )
                }
                items(
                    items = state.categories,
                    key = LiveCategory::providerCategoryKey,
                ) { category ->
                    CategoryRowCompact(
                        title = category.name,
                        countLabel = null,
                        selected = state.query.categoryKey == category.providerCategoryKey,
                        onClick = { onCategorySelected(category.providerCategoryKey) },
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CategoryRowCompact(
    title: String,
    countLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(LIVE_MOTION_FAST),
        label = "categorySelection",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                ),
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (countLabel != null) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelColumn(
    state: LiveBrowseState,
    playingChannelId: String?,
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val showSearch = searchExpanded || state.query.searchTerm.isNotBlank()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactLiveToolbar(
                state = state,
                showSearch = showSearch,
                onToggleSearch = {
                    if (showSearch) {
                        onSearchChange("")
                        searchExpanded = false
                    } else {
                        searchExpanded = true
                    }
                },
                onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                onOrderChanged = onOrderChanged,
                onCustomGroupSelected = onCustomGroupSelected,
            )
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(LIVE_MOTION_FAST)),
                exit = fadeOut(tween(LIVE_MOTION_FAST)),
            ) {
                OutlinedTextField(
                    value = state.query.searchTerm,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    singleLine = true,
                    placeholder = { Text("Search") },
                    shape = RoundedCornerShape(10.dp),
                )
            }
            HorizontalDivider()
            ChannelListCompact(
                channels = state.channels,
                playingChannelId = playingChannelId,
                onChannelSelected = onChannelSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChannelListCompact(
    channels: List<LiveChannelItem>,
    playingChannelId: String?,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No matching channels",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = channels,
            key = LiveChannelItem::channelId,
        ) { channel ->
            CompactChannelRow(
                channel = channel,
                playing = channel.channelId == playingChannelId,
                onClick = { onChannelSelected(channel.channelId) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
        }
    }
}

@Composable
private fun CompactChannelRow(
    channel: LiveChannelItem,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (playing) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(LIVE_MOTION_FAST),
        label = "channelPlaying",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.displayName.trim().firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
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
            channel.categoryName?.takeIf(String::isNotBlank)?.let { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (channel.isFavorite) {
            Text(
                text = "★",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (playing) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaybackAndEpgColumn(
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
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (preview != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
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
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(availableHeight * 0.30f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Select a channel to start Live preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "EPG",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (preview != null) {
                        TextButton(
                            onClick = onOpenEpgGuide,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Guide", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                LandscapeEpgList(
                    snapshot = epgSnapshot,
                    loading = epgLoading,
                    failed = epgFailed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LandscapeEpgList(
    snapshot: EpgSnapshot?,
    loading: Boolean,
    failed: Boolean,
    modifier: Modifier,
) {
    when {
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Updating EPG…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        failed -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "EPG unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        snapshot == null || snapshot.programs.isEmpty() -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No EPG for this channel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            val timeline = EpgTimelineProjector.project(
                programs = snapshot.programs,
                nowEpochSeconds = System.currentTimeMillis() / 1_000L,
            )
            val programs = buildList {
                timeline.past.takeLast(2).forEach(::add)
                timeline.current?.let(::add)
                timeline.future.take(8).forEach(::add)
            }
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(programs) { program ->
                    val current = program == timeline.current
                    val background by animateColorAsState(
                        targetValue = if (current) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(LIVE_MOTION_FAST),
                        label = "epgCurrent",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(background)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = program.startLabel ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = program.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current) {
                            Text(
                                text = "NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactOrderMenu(
    selected: LiveBrowseOrder,
    onSelected: (LiveBrowseOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompactAction(
            label = "⇅",
            selected = selected != LiveBrowseOrder.PROVIDER,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf(
                LiveBrowseOrder.PROVIDER to "Provider",
                LiveBrowseOrder.MY_ORDER to "My order",
                LiveBrowseOrder.RECENTLY_WATCHED to "Recent",
                LiveBrowseOrder.A_TO_Z to "A–Z",
                LiveBrowseOrder.Z_TO_A to "Z–A",
                LiveBrowseOrder.CATEGORY to "Category",
            ).forEach { (order, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selected == order) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
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
private fun CompactGroupMenu(
    groups: List<Pair<String, String>>,
    selectedGroupId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompactAction(
            label = "Group",
            selected = selectedGroupId != null,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
private fun PaneDivider() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
