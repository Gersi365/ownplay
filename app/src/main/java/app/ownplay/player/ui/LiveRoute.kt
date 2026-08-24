package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import app.ownplay.player.playback.LiveChannelSelectionAction
import app.ownplay.player.playback.LiveChannelSelectionRouter
import app.ownplay.player.playback.LivePlaybackBrowseContext
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.ui.live.LandscapeLiveWorkspaceSimple
import app.ownplay.player.ui.live.PortraitLiveBrowse
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
    val browseState by browseFlow.collectAsState(initial = LiveBrowseState())
    val mutationScope = rememberCoroutineScope()
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

    val syncForThisSource = syncState.sourceId == sourceId
    val loadingChannels = syncForThisSource && syncState.stage == SourceSyncStage.LoadingChannels
    val loadingEpg = syncForThisSource && syncState.stage == SourceSyncStage.LoadingEpg
    val channelRefreshFailed = syncForThisSource && syncState.stage == SourceSyncStage.ChannelsFailed
    val epgRefreshFailed = syncForThisSource && syncState.stage == SourceSyncStage.EpgFailed

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

    fun selectChannel(channelId: String) {
        val channel = browseState.channels.firstOrNull { item -> item.channelId == channelId } ?: return
        val browseContext = LivePlaybackBrowseContext.capture(
            sourceId = sourceId,
            visibleChannels = browseState.channels,
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

    if (isLandscape) {
        LandscapeLiveWorkspaceSimple(
            state = browseState,
            preview = preview,
            playbackState = playbackState,
            videoOutput = videoOutput,
            epgSnapshot = epgSnapshot,
            epgLoading = loadingEpg || epgLookupLoading,
            epgFailed = epgRefreshFailed || epgLookupFailed,
            onSearchChange = browseSession::updateSearch,
            onCategorySelected = browseSession::selectCategory,
            onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
            onOrderChanged = browseSession::setOrder,
            onCustomGroupSelected = browseSession::selectCustomGroup,
            onChannelSelected = ::selectChannel,
            onPlay = onPlay,
            onPause = onPause,
            onRetry = onRetry,
            onNavigatePreview = onNavigatePreview,
            onOpenFullscreen = onOpenFullscreen,
            onPreviewClosed = onPreviewClosed,
            onOpenEpgGuide = { showEpgGuide = true },
            onOpenMovies = onOpenMovies,
            onOpenSeries = onOpenSeries,
            onOpenSettings = onOpenSettings,
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (preview != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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

            when {
                loadingChannels -> CompactSyncStatus(
                    text = if (browseState.catalogChannelCount == 0) {
                        "Loading channels…"
                    } else {
                        "Updating channels…"
                    },
                )
                loadingEpg -> CompactSyncStatus(text = "Updating EPG…")
                channelRefreshFailed -> CompactRetryStatus(
                    text = "Channel refresh failed. Existing channels were kept.",
                    actionLabel = "Retry",
                    onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                )
                epgRefreshFailed -> CompactRetryStatus(
                    text = "EPG unavailable. Live remains usable.",
                    actionLabel = "Retry EPG",
                    onAction = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                )
            }

            if (browseState.catalogChannelCount == 0 && !loadingChannels) {
                LiveConsumerEmptyState(
                    failed = channelRefreshFailed,
                    onRetry = { mutationScope.launch { runtime.refreshSource(sourceId) } },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitLiveBrowse(
                    state = browseState,
                    playingChannelId = preview?.request?.channelId,
                    onSearchChange = browseSession::updateSearch,
                    onCategorySelected = browseSession::selectCategory,
                    onFavoritesOnlyChanged = browseSession::setFavoritesOnly,
                    onOrderChanged = browseSession::setOrder,
                    onCustomGroupSelected = browseSession::selectCustomGroup,
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
}

@Composable
private fun CompactSyncStatus(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CompactRetryStatus(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onAction) {
            Text(actionLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiveConsumerEmptyState(
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
    ) {
        Text(
            text = if (failed) "Channels could not be refreshed" else "No Live channels",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
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
