package app.ownplay.player.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.ownplay.player.epg.EpgProgram
import app.ownplay.player.epg.EpgSnapshot
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput
import app.ownplay.player.ui.EpgPanel
import app.ownplay.player.ui.LivePreviewPanel
import app.ownplay.player.ui.tv.LocalTvShellFocusBoundary
import app.ownplay.player.ui.view.ContentViewMode

/**
 * TV-only Live presentation workspace.
 *
 * Data, filtering, ordering, EPG loading and playback ownership stay outside this component. The
 * workspace owns only TV geometry and focus continuity: browse remains the primary remote target,
 * Preview is presentation-only, and selected-channel EPG stays beside Preview.
 */
@Composable
internal fun TvLiveWorkspace(
    state: LiveBrowseState,
    hierarchyLevel: LiveBrowseHierarchyLevel,
    preview: LivePlaybackSelection?,
    playbackState: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    epgSnapshot: EpgSnapshot?,
    currentEpgByChannelId: Map<String, EpgProgram>,
    epgLoading: Boolean,
    epgFailed: Boolean,
    viewMode: ContentViewMode,
    onViewModeSelected: (ContentViewMode) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val shellFocusBoundary = LocalTvShellFocusBoundary.current
    val channelFocusRequester = remember { FocusRequester() }
    var focusChannelId by remember { mutableStateOf<String?>(null) }
    var channelFocusRequestGeneration by remember { mutableIntStateOf(0) }
    var initialChannelFocusRequested by remember { mutableStateOf(false) }
    var previousPreviewChannelId by remember { mutableStateOf<String?>(null) }

    fun requestChannelFocus(preferredChannelId: String?) {
        val visibleTarget = preferredChannelId?.takeIf { candidate ->
            state.channels.any { channel -> channel.channelId == candidate }
        }
        val target = visibleTarget ?: state.channels.firstOrNull()?.channelId ?: return
        focusChannelId = target
        channelFocusRequestGeneration += 1
    }

    LaunchedEffect(
        hierarchyLevel,
        state.channels.firstOrNull()?.channelId,
        preview?.request?.channelId,
    ) {
        if (hierarchyLevel == LiveBrowseHierarchyLevel.CATEGORIES) {
            initialChannelFocusRequested = false
            focusChannelId = null
            previousPreviewChannelId = preview?.request?.channelId
            return@LaunchedEffect
        }

        val currentPreviewChannelId = preview?.request?.channelId
        if (!initialChannelFocusRequested && state.channels.isNotEmpty()) {
            initialChannelFocusRequested = true
            requestChannelFocus(currentPreviewChannelId ?: state.channels.first().channelId)
        }
        if (currentPreviewChannelId == null && previousPreviewChannelId != null) {
            requestChannelFocus(previousPreviewChannelId)
        }
        previousPreviewChannelId = currentPreviewChannelId
    }

    LaunchedEffect(
        shellFocusBoundary.contentEntryGeneration,
        hierarchyLevel,
        state.channels.firstOrNull()?.channelId,
        preview?.request?.channelId,
    ) {
        if (shellFocusBoundary.contentEntryGeneration <= 0) return@LaunchedEffect
        if (hierarchyLevel == LiveBrowseHierarchyLevel.CHANNELS) {
            requestChannelFocus(preview?.request?.channelId ?: focusChannelId)
        }
    }

    val onShellLeftBoundary: () -> Boolean = {
        if (shellFocusBoundary.railVisible) {
            shellFocusBoundary.requestRailFocus()
            true
        } else {
            false
        }
    }

    if (preview == null) {
        TvLiveBrowseSurface(
            state = state,
            hierarchyLevel = hierarchyLevel,
            playingChannelId = null,
            currentEpgByChannelId = currentEpgByChannelId,
            viewMode = viewMode,
            onViewModeSelected = onViewModeSelected,
            onSearchChange = onSearchChange,
            onCategorySelected = onCategorySelected,
            onFavoritesOnlyChanged = onFavoritesOnlyChanged,
            onOrderChanged = onOrderChanged,
            onCustomGroupSelected = onCustomGroupSelected,
            onChannelSelected = onChannelSelected,
            focusChannelId = focusChannelId,
            focusRequestGeneration = channelFocusRequestGeneration,
            channelFocusRequester = channelFocusRequester,
            contentEntryGeneration = shellFocusBoundary.contentEntryGeneration,
            onLeftBoundary = onShellLeftBoundary,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvLiveBrowseSurface(
            state = state,
            hierarchyLevel = hierarchyLevel,
            playingChannelId = preview.request.channelId,
            currentEpgByChannelId = currentEpgByChannelId,
            viewMode = viewMode,
            onViewModeSelected = onViewModeSelected,
            onSearchChange = onSearchChange,
            onCategorySelected = onCategorySelected,
            onFavoritesOnlyChanged = onFavoritesOnlyChanged,
            onOrderChanged = onOrderChanged,
            onCustomGroupSelected = onCustomGroupSelected,
            onChannelSelected = onChannelSelected,
            focusChannelId = focusChannelId,
            focusRequestGeneration = channelFocusRequestGeneration,
            channelFocusRequester = channelFocusRequester,
            contentEntryGeneration = shellFocusBoundary.contentEntryGeneration,
            onLeftBoundary = onShellLeftBoundary,
            modifier = Modifier
                .weight(0.59f)
                .fillMaxHeight(),
        )

        Surface(
            modifier = Modifier
                .weight(0.41f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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

                EpgPanel(
                    snapshot = epgSnapshot,
                    loading = epgLoading,
                    failed = epgFailed,
                    onOpenGuide = onOpenEpgGuide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionLeft
                            ) {
                                channelFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun TvLiveBrowseSurface(
    state: LiveBrowseState,
    hierarchyLevel: LiveBrowseHierarchyLevel,
    playingChannelId: String?,
    currentEpgByChannelId: Map<String, EpgProgram>,
    viewMode: ContentViewMode,
    onViewModeSelected: (ContentViewMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onOrderChanged: (LiveBrowseOrder) -> Unit,
    onCustomGroupSelected: (String?) -> Unit,
    onChannelSelected: (String) -> Unit,
    focusChannelId: String?,
    focusRequestGeneration: Int,
    channelFocusRequester: FocusRequester,
    contentEntryGeneration: Int,
    onLeftBoundary: () -> Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        tonalElevation = 0.dp,
    ) {
        TvLiveChannelBrowser(
            state = state,
            hierarchyLevel = hierarchyLevel,
            playingChannelId = playingChannelId,
            onCategorySelected = onCategorySelected,
            onChannelSelected = onChannelSelected,
            focusChannelId = focusChannelId,
            focusRequestGeneration = focusRequestGeneration,
            channelFocusRequester = channelFocusRequester,
            contentEntryGeneration = contentEntryGeneration,
            onLeftBoundary = onLeftBoundary,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
