package app.ownplay.player.ui.live

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.live.LiveChannelLogoResolver
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackBrowseContext
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.network.SourceHttpClient
import app.ownplay.player.ui.EpgGuideSheet
import app.ownplay.player.ui.EpgPanel
import app.ownplay.player.ui.LivePreviewPanel
import app.ownplay.player.ui.view.ContentViewMode
import app.ownplay.player.ui.view.ContentViewModeMenu
import app.ownplay.player.ui.view.ContentViewModeStore
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val TV_MAX_CHANNEL_LOGO_BYTES = 2 * 1024 * 1024
private const val TV_MAX_CHANNEL_LOGO_EDGE_PX = 256

@Composable
internal fun TvLiveRoute(
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val browseSession = remember(sourceId) { LiveBrowseSession() }
    val browseFlow = remember(sourceId) { browseSession.observe(runtime.observeLiveCatalog(sourceId)) }
    val state by browseFlow.collectAsState(initial = LiveBrowseState())
    val scope = rememberCoroutineScope()
    val viewStore = remember(context) { ContentViewModeStore(context.applicationContext) }
    val viewMode by viewStore.liveMode.collectAsState(initial = ContentViewMode.COMPACT)
    val preview = activeSelection?.takeIf { it.request.sourceId == sourceId }
    val categoryFocusRequester = remember(sourceId) { FocusRequester() }
    val firstChannelFocusRequester = remember(sourceId) { FocusRequester() }

    var epgSnapshot by remember(sourceId, preview?.request?.channelId) { mutableStateOf<EpgSnapshot?>(null) }
    var currentEpgByChannelId by remember(sourceId) { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }
    var epgLookupLoading by remember(sourceId, preview?.request?.channelId) { mutableStateOf(false) }
    var epgLookupFailed by remember(sourceId, preview?.request?.channelId) { mutableStateOf(false) }
    var showEpgGuide by remember(sourceId, preview?.request?.channelId) { mutableStateOf(false) }
    var initialCategoryFocusRequested by remember(sourceId) { mutableStateOf(false) }

    val syncForSource = syncState.sourceId == sourceId
    val loadingChannels = syncForSource && syncState.stage == SourceSyncStage.LoadingChannels
    val loadingEpg = syncForSource && syncState.stage == SourceSyncStage.LoadingEpg
    val refreshFailed = syncForSource && syncState.stage == SourceSyncStage.ChannelsFailed

    LaunchedEffect(state.categories, state.query.categoryKey) {
        val categories = state.categories
        val selected = state.query.categoryKey
        if (categories.isNotEmpty() && categories.none { it.providerCategoryKey == selected }) {
            browseSession.selectCategory(categories.first().providerCategoryKey)
        }
    }

    LaunchedEffect(state.categories, state.query.categoryKey, initialCategoryFocusRequested) {
        if (
            !initialCategoryFocusRequested &&
            state.categories.any { it.providerCategoryKey == state.query.categoryKey }
        ) {
            categoryFocusRequester.requestFocus()
            initialCategoryFocusRequested = true
        }
    }

    LaunchedEffect(sourceId, syncState.sourceId, syncState.stage) {
        if (loadingEpg) return@LaunchedEffect
        while (true) {
            currentEpgByChannelId = runtime.currentEpgPrograms(sourceId)
            delay(30_000L)
        }
    }

    LaunchedEffect(preview?.request?.channelId, syncState.sourceId, syncState.stage) {
        val selected = preview
        if (selected == null || loadingEpg) {
            epgSnapshot = null
            epgLookupLoading = false
            epgLookupFailed = false
            return@LaunchedEffect
        }
        epgLookupLoading = true
        epgLookupFailed = false
        try {
            epgSnapshot = runtime.epgSnapshot(sourceId, selected.request.channelId)
            currentEpgByChannelId = runtime.currentEpgPrograms(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            epgSnapshot = null
            epgLookupFailed = true
        } finally {
            epgLookupLoading = false
        }
    }

    fun selectChannel(channelId: String) {
        val channel = state.channels.firstOrNull { it.channelId == channelId } ?: return
        val browseContext = LivePlaybackBrowseContext.capture(sourceId, state.channels)
        when (val action = LiveChannelSelectionRouter.route(channel, false, browseContext)) {
            is LiveChannelSelectionAction.StartPlayback -> onPreviewRequested(action.selection)
            is LiveChannelSelectionAction.ToggleEditSelection -> Unit
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvLiveCategoryRail(
            categories = state.categories,
            selectedCategoryKey = state.query.categoryKey,
            hasChannels = state.channels.isNotEmpty(),
            selectedFocusRequester = categoryFocusRequester,
            onMoveToChannels = { firstChannelFocusRequester.requestFocus() },
            onCategorySelected = browseSession::selectCategory,
            modifier = Modifier
                .width(216.dp)
                .fillMaxHeight(),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvLiveToolbar(
                state = state,
                viewMode = viewMode,
                onViewModeSelected = { mode -> scope.launch { viewStore.setLiveMode(mode) } },
                onSearchChange = browseSession::updateSearch,
                onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
                onOrderChanged = browseSession::setOrder,
                onCustomGroupSelected = browseSession::selectCustomGroup,
            )

            when {
                loadingChannels && state.catalogChannelCount == 0 -> TvLiveStatus("Loading channels…")
                refreshFailed && state.catalogChannelCount == 0 -> TvLiveFailure(
                    onRetry = { scope.launch { runtime.refreshSource(sourceId) } },
                    onOpenSettings = onOpenSettings,
                )
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvLiveChannelView(
                    channels = state.channels,
                    playingChannelId = preview?.request?.channelId,
                    currentEpgByChannelId = currentEpgByChannelId,
                    viewMode = viewMode,
                    firstItemFocusRequester = firstChannelFocusRequester,
                    onMoveToCategories = { categoryFocusRequester.requestFocus() },
                    onChannelSelected = ::selectChannel,
                    modifier = Modifier
                        .weight(if (preview == null) 1f else 0.64f)
                        .fillMaxHeight(),
                )

                if (preview != null) {
                    Surface(
                        modifier = Modifier.weight(0.36f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider()
                            EpgPanel(
                                snapshot = epgSnapshot,
                                loading = loadingEpg || epgLookupLoading,
                                failed = epgLookupFailed,
                                onOpenGuide = { showEpgGuide = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEpgGuide && preview != null) {
        EpgGuideSheet(
            channelName = preview.displayName,
            snapshot = epgSnapshot,
            loading = loadingEpg || epgLookupLoading,
            failed = epgLookupFailed,
            onDismiss = { showEpgGuide = false },
        )
    }
}

@Composable
private fun TvLiveCategoryRail(
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    hasChannels: Boolean,
    selectedFocusRequester: FocusRequester,
    onMoveToChannels: () -> Unit,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(categories, key = LiveCategory::providerCategoryKey) { category ->
                    var focused by remember(category.providerCategoryKey) { mutableStateOf(false) }
                    val selected = category.providerCategoryKey == selectedCategoryKey
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (selected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (
                                    hasChannels &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionRight
                                ) {
                                    onMoveToChannels()
                                    true
                                } else {
                                    false
                                }
                            }
                            .clickable { onCategorySelected(category.providerCategoryKey) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected || focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLiveToolbar(
    state: LiveBrowseState,
    viewMode: ContentViewMode,
    onViewModeSelected: (ContentViewMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.channels.size} channels",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onFavoritesOnlyChanged(!state.query.favoritesOnly) }) {
                Icon(
                    if (state.query.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorites",
                    tint = if (state.query.favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TvLiveBrowseMenu(state, onOrderChanged, onCustomGroupSelected)
            ContentViewModeMenu(viewMode, onViewModeSelected, prefix = "View")
            IconButton(onClick = {
                searchExpanded = !searchExpanded
                if (!searchExpanded) onSearchChange("")
            }) {
                Icon(if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Search")
            }
        }
        if (searchExpanded || state.query.searchTerm.isNotBlank()) {
            OutlinedTextField(
                value = state.query.searchTerm,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search channels") },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun TvLiveBrowseMenu(
    state: LiveBrowseState,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Browse") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LiveBrowseOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.tvLabel() + if (order == state.query.order) " ✓" else "") },
                    onClick = {
                        expanded = false
                        onOrderChanged(order)
                    },
                )
            }
            if (state.customGroups.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (state.query.customGroupId == null) "No group filter ✓" else "No group filter") },
                    onClick = {
                        expanded = false
                        onCustomGroupSelected(null)
                    },
                )
                state.customGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.name + if (state.query.customGroupId == group.groupId) " ✓" else "") },
                        onClick = {
                            expanded = false
                            onCustomGroupSelected(group.groupId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvLiveChannelView(
    channels: List<LiveChannelItem>,
    playingChannelId: String?,
    currentEpgByChannelId: Map<String, EpgProgram>,
    viewMode: ContentViewMode,
    firstItemFocusRequester: FocusRequester,
    onMoveToCategories: () -> Unit,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No channels in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    when (viewMode) {
        ContentViewMode.LIST,
        ContentViewMode.COMPACT,
        -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (viewMode == ContentViewMode.COMPACT) 3.dp else 6.dp),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.channelId }) { index, channel ->
                TvLiveChannelRow(
                    channel = channel,
                    playing = channel.channelId == playingChannelId,
                    currentProgram = currentEpgByChannelId[channel.channelId],
                    compact = viewMode == ContentViewMode.COMPACT,
                    onMoveToCategories = onMoveToCategories,
                    onClick = { onChannelSelected(channel.channelId) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                )
            }
        }
        ContentViewMode.CARDS -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 126.dp),
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            gridItemsIndexed(channels, key = { _, channel -> channel.channelId }) { index, channel ->
                TvLiveChannelCard(
                    channel = channel,
                    playing = channel.channelId == playingChannelId,
                    currentProgram = currentEpgByChannelId[channel.channelId],
                    onMoveToCategories = onMoveToCategories,
                    onClick = { onChannelSelected(channel.channelId) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun TvLiveChannelRow(
    channel: LiveChannelItem,
    playing: Boolean,
    currentProgram: EpgProgram?,
    compact: Boolean,
    onMoveToCategories: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(channel.channelId) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onMoveToCategories()
                    true
                } else {
                    false
                }
            }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (playing || focused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TvRemoteChannelLogo(channel.logoRef, channel.displayName, Modifier.size(if (compact) 34.dp else 42.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                currentProgram?.title?.let { title ->
                    Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (channel.isFavorite) Text("★", color = MaterialTheme.colorScheme.primary)
            if (playing) Text("▶", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TvLiveChannelCard(
    channel: LiveChannelItem,
    playing: Boolean,
    currentProgram: EpgProgram?,
    onMoveToCategories: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(channel.channelId) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onMoveToCategories()
                    true
                } else {
                    false
                }
            }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (playing || focused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (focused) 3.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                TvRemoteChannelLogo(channel.logoRef, channel.displayName, Modifier.size(42.dp))
            }
            Text(
                text = channel.displayName,
                modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            currentProgram?.title?.let { title ->
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (channel.isFavorite || playing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (channel.isFavorite) Text("★", color = MaterialTheme.colorScheme.primary)
                    if (playing) Text("▶", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TvRemoteChannelLogo(url: String?, title: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = remember(context) { LiveChannelLogoResolver(context.applicationContext) }
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = resolver.resolve(url)?.takeIf(String::isNotBlank)?.let { loadTvChannelLogo(it) }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { bitmap ->
            Image(bitmap, contentDescription = title, modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        } ?: Text(title.trim().firstOrNull()?.uppercase() ?: "•", fontWeight = FontWeight.Bold)
    }
}

private suspend fun loadTvChannelLogo(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        SourceHttpClient.shared.newCall(Request.Builder().url(url).get().build()).execute().use responseUse@ { response ->
            if (!response.isSuccessful) return@responseUse null
            val body = response.body
            if (body.contentLength() > TV_MAX_CHANNEL_LOGO_BYTES.toLong()) return@responseUse null
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > TV_MAX_CHANNEL_LOGO_BYTES) return@responseUse null
                    output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > TV_MAX_CHANNEL_LOGO_EDGE_PX || bounds.outHeight / sample > TV_MAX_CHANNEL_LOGO_EDGE_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun TvLiveStatus(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TvLiveFailure(onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Channels could not be refreshed", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}

private fun LiveBrowseOrder.tvLabel(): String = when (this) {
    LiveBrowseOrder.PROVIDER -> "Provider order"
    LiveBrowseOrder.MY_ORDER -> "My order"
    LiveBrowseOrder.FAVORITE_ORDER -> "Favorite order"
    LiveBrowseOrder.RECENTLY_WATCHED -> "Recently watched"
    LiveBrowseOrder.A_TO_Z -> "A–Z"
    LiveBrowseOrder.Z_TO_A -> "Z–A"
    LiveBrowseOrder.CATEGORY -> "Category"
}
