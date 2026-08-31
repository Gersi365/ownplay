package app.ownplay.player.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val MOBILE_LOGO_MAX_BYTES = 2 * 1024 * 1024
private const val MOBILE_LOGO_MAX_EDGE_PX = 256

/**
 * Mobile-only Live surface.
 *
 * Categories are filters only. Channel identity is logo + channel name, with current EPG as the
 * only optional secondary line. Provider category names are deliberately never rendered inside a
 * channel row/card.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun TargetLiveRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    syncState: SourceSyncState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
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
    val state by browseFlow.collectAsState(initial = LiveBrowseState())
    val scope = rememberCoroutineScope()
    val channelListState = rememberLazyListState()
    val preview = activeSelection?.takeIf { it.request.sourceId == sourceId }

    var searchExpanded by remember(sourceId) { mutableStateOf(false) }
    var epgSnapshot by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf<EpgSnapshot?>(null)
    }
    var currentEpgByChannelId by remember(sourceId) {
        mutableStateOf<Map<String, EpgProgram>>(emptyMap())
    }
    var epgLookupLoading by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf(false)
    }
    var epgLookupFailed by remember(sourceId, preview?.request?.channelId) {
        mutableStateOf(false)
    }
    var showEpgGuide by remember(sourceId) { mutableStateOf(false) }

    val syncForSource = syncState.sourceId == sourceId
    val loadingChannels = syncForSource && syncState.stage == SourceSyncStage.LoadingChannels
    val loadingEpg = syncForSource && syncState.stage == SourceSyncStage.LoadingEpg
    val channelRefreshFailed = syncForSource && syncState.stage == SourceSyncStage.ChannelsFailed
    val epgRefreshFailed = syncForSource && syncState.stage == SourceSyncStage.EpgFailed
    val selectedEpgFailed = epgLookupFailed || (
        epgRefreshFailed && epgSnapshot?.programs.isNullOrEmpty()
    )

    BackHandler(enabled = preview != null) {
        onPreviewClosed()
    }

    LaunchedEffect(state.categories, state.query.categoryKey) {
        val categories = state.categories
        if (categories.isEmpty()) return@LaunchedEffect
        val selected = state.query.categoryKey
        if (selected == null || categories.none { it.providerCategoryKey == selected }) {
            browseSession.selectCategory(categories.first().providerCategoryKey)
        }
    }

    LaunchedEffect(sourceId, syncState.sourceId, syncState.stage) {
        if (loadingEpg) return@LaunchedEffect
        while (true) {
            try {
                currentEpgByChannelId = runtime.currentEpgPrograms(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last known EPG map. Live browsing must remain usable without EPG.
            }
            delay(30_000L)
        }
    }

    LaunchedEffect(preview?.request?.channelId, syncState.sourceId, syncState.stage) {
        showEpgGuide = preview != null && LiveEpgPresentationBridge.consumeFullGuideRequest()
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
            epgSnapshot = runtime.epgSnapshot(
                sourceId = sourceId,
                channelId = selected.request.channelId,
            )
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
        val currentPreview = preview
        if (currentPreview?.request?.channelId == channelId) {
            onOpenFullscreen(currentPreview)
            return
        }
        val channel = state.channels.firstOrNull { it.channelId == channelId } ?: return
        val browseContext = LivePlaybackBrowseContext.capture(
            sourceId = sourceId,
            visibleChannels = state.channels,
        )
        when (
            val action = LiveChannelSelectionRouter.route(
                channel = channel,
                isEditing = false,
                browseContext = browseContext,
            )
        ) {
            is LiveChannelSelectionAction.StartPlayback -> onPreviewRequested(action.selection)
            is LiveChannelSelectionAction.ToggleEditSelection -> Unit
        }
    }

    val browseContent: @Composable (Modifier) -> Unit = { modifier ->
        MobileLiveBrowsePane(
            state = state,
            playingChannelId = preview?.request?.channelId,
            currentEpgByChannelId = currentEpgByChannelId,
            channelListState = channelListState,
            searchExpanded = searchExpanded,
            onSearchExpandedChange = { searchExpanded = it },
            onSearchChange = browseSession::updateSearch,
            onCategorySelected = browseSession::selectCategory,
            onChannelSelected = ::selectChannel,
            loadingChannels = loadingChannels,
            channelRefreshFailed = channelRefreshFailed,
            onRetry = { scope.launch { runtime.refreshSource(sourceId) } },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    }

    if (isLandscape && preview != null) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            browseContent(Modifier.weight(0.43f).fillMaxHeight())
            Column(
                modifier = Modifier.weight(0.57f).fillMaxHeight(),
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
                    showLiveBadge = false,
                )
                EpgPanel(
                    snapshot = epgSnapshot,
                    loading = loadingEpg || epgLookupLoading,
                    failed = selectedEpgFailed,
                    onOpenGuide = { showEpgGuide = true },
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (preview != null) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        showLiveBadge = false,
                    )
                    EpgPanel(
                        snapshot = epgSnapshot,
                        loading = loadingEpg || epgLookupLoading,
                        failed = selectedEpgFailed,
                        onOpenGuide = { showEpgGuide = true },
                    )
                }
            }
            browseContent(Modifier.weight(1f))
        }
    }

    if (showEpgGuide && preview != null) {
        EpgGuideSheet(
            channelName = preview.displayName,
            snapshot = epgSnapshot,
            loading = loadingEpg || epgLookupLoading,
            failed = selectedEpgFailed,
            onDismiss = { showEpgGuide = false },
        )
    }
}

@Composable
private fun MobileLiveBrowsePane(
    state: LiveBrowseState,
    playingChannelId: String?,
    currentEpgByChannelId: Map<String, EpgProgram>,
    channelListState: LazyListState,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    loadingChannels: Boolean,
    channelRefreshFailed: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${state.channels.size} channels",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = !searchExpanded
                            onSearchExpandedChange(next)
                            if (!next) onSearchChange("")
                        },
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchExpanded) "Close search" else "Search channels",
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = searchExpanded || state.query.searchTerm.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                OutlinedTextField(
                    value = state.query.searchTerm,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Search channels") },
                    shape = RoundedCornerShape(10.dp),
                )
            }

            if (state.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.categories,
                        key = { it.providerCategoryKey },
                    ) { category ->
                        FilterChip(
                            selected = state.query.categoryKey == category.providerCategoryKey,
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                loadingChannels && state.catalogChannelCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.catalogChannelCount == 0 -> MobileLiveEmptyState(
                    failed = channelRefreshFailed,
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
                state.channels.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matching channels",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    state = channelListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = state.channels,
                        key = { it.channelId },
                    ) { channel ->
                        MobileChannelRow(
                            channel = channel,
                            currentProgram = currentEpgByChannelId[channel.channelId],
                            active = channel.channelId == playingChannelId,
                            onClick = { onChannelSelected(channel.channelId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileChannelRow(
    channel: LiveChannelItem,
    currentProgram: EpgProgram?,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileChannelLogo(
            logoRef = channel.logoRef,
            title = channel.displayName,
            modifier = Modifier.size(46.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            currentProgram?.let { program ->
                Text(
                    text = buildString {
                        program.startLabel?.takeIf(String::isNotBlank)?.let { start ->
                            append(start)
                            append(" · ")
                        }
                        append(program.title)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (channel.isFavorite) {
            Text(
                text = "★",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun MobileChannelLogo(
    logoRef: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolver = remember(context) { LiveChannelLogoResolver(context.applicationContext) }
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = logoRef) {
        value = resolver.resolve(logoRef)
            ?.takeIf(String::isNotBlank)
            ?.let { resolvedUrl -> loadMobileChannelLogo(resolvedUrl) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } ?: Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "•",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun loadMobileChannelLogo(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        SourceHttpClient.shared.newCall(
            Request.Builder().url(url).get().build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body
            if (body.contentLength() > MOBILE_LOGO_MAX_BYTES.toLong()) return@use null
            val bytes = readMobileLogoBytes(body.byteStream()) ?: return@use null
            decodeMobileLogo(bytes)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private fun readMobileLogoBytes(input: java.io.InputStream): ByteArray? {
    val output = ByteArrayOutputStream(64 * 1024)
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > MOBILE_LOGO_MAX_BYTES) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun decodeMobileLogo(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MOBILE_LOGO_MAX_EDGE_PX ||
        bounds.outHeight / sampleSize > MOBILE_LOGO_MAX_EDGE_PX
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )?.asImageBitmap()
}

@Composable
private fun MobileLiveEmptyState(
    failed: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (failed) "Channels could not be refreshed" else "No Live channels",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(1.dp))
        Text(
            text = "Check the playlist or refresh it from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}
