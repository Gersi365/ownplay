package app.ownplay.player.ui.live

import android.content.res.Configuration
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
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
import app.ownplay.player.ui.view.ContentViewMode

/**
 * Landscape Live shell with one consistent browse model across touch and TV layouts.
 *
 * Live browsing now starts at category level. After a category is chosen the established
 * List / Compact / Cards channel browser is shown. Before a channel is selected, browsing owns
 * the full workspace. Once a channel is selected, the browse area remains available on the left
 * while the selected channel owns a dedicated playback/EPG panel on the right.
 *
 * TV keeps channel focus in the browser after the first OK. The preview has no TV controls, so a
 * second OK on the same channel is routed by LiveRoute to Fullscreen. Back/ESC precedence is also
 * owned by LiveRoute: close Preview first, then return Channels -> Categories, then propagate.
 */
@Composable
internal fun LandscapeLiveWorkspaceAdaptive(
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
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val channelFocusRequester = remember { FocusRequester() }
    val previewFocusRequester = remember { FocusRequester() }
    val epgFocusRequester = remember { FocusRequester() }
    var focusChannelId by remember { mutableStateOf<String?>(null) }
    var channelFocusRequestGeneration by remember { mutableIntStateOf(0) }
    var initialBrowserFocusRequested by remember { mutableStateOf(false) }
    var previousPreviewChannelId by remember { mutableStateOf<String?>(null) }
    var lastPreviewChannelId by remember { mutableStateOf<String?>(null) }

    fun requestChannelFocus(preferredChannelId: String?) {
        val visibleTarget = preferredChannelId?.takeIf { candidate ->
            state.channels.any { channel -> channel.channelId == candidate }
        }
        val target = visibleTarget ?: state.channels.firstOrNull()?.channelId ?: return
        focusChannelId = target
        channelFocusRequestGeneration += 1
    }

    fun applyFocusAction(
        zone: LandscapeLiveFocusZone,
        action: LandscapeLiveFocusAction,
    ): Boolean {
        val destination = LandscapeLiveFocusPolicy.destination(
            current = zone,
            action = action,
            hasPreview = preview != null,
        ) ?: return false
        when (destination) {
            LandscapeLiveFocusZone.BROWSER -> {
                requestChannelFocus(preview?.request?.channelId ?: lastPreviewChannelId)
            }
            LandscapeLiveFocusZone.PREVIEW -> previewFocusRequester.requestFocus()
            LandscapeLiveFocusZone.EPG -> epgFocusRequester.requestFocus()
        }
        return true
    }

    LaunchedEffect(
        hierarchyLevel,
        state.channels.firstOrNull()?.channelId,
        preview?.request?.channelId,
    ) {
        if (hierarchyLevel == LiveBrowseHierarchyLevel.CATEGORIES) {
            initialBrowserFocusRequested = false
            focusChannelId = null
            previousPreviewChannelId = preview?.request?.channelId
            return@LaunchedEffect
        }

        val currentPreviewChannelId = preview?.request?.channelId
        if (currentPreviewChannelId != null) {
            lastPreviewChannelId = currentPreviewChannelId
        }
        if (!initialBrowserFocusRequested && state.channels.isNotEmpty()) {
            initialBrowserFocusRequested = true
            requestChannelFocus(currentPreviewChannelId ?: state.channels.first().channelId)
        }
        if (currentPreviewChannelId == null && previousPreviewChannelId != null) {
            requestChannelFocus(previousPreviewChannelId)
        }
        previousPreviewChannelId = currentPreviewChannelId
    }

    if (preview == null) {
        LandscapeBrowseSurface(
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
            onPreviewKeyEvent = { false },
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LandscapeBrowseSurface(
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
            onPreviewKeyEvent = { event ->
                if (event.isKeyDown(Key.DirectionRight)) {
                    if (
                        LandscapeLiveFocusPolicy.consumeBrowserRight(
                            isTelevision = isTelevision,
                            hasPreview = preview != null,
                        )
                    ) {
                        true
                    } else {
                        applyFocusAction(
                            zone = LandscapeLiveFocusZone.BROWSER,
                            action = LandscapeLiveFocusAction.RIGHT,
                        )
                    }
                } else {
                    false
                }
            },
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
        )

        Surface(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(previewFocusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.isKeyDown(Key.DirectionLeft) -> applyFocusAction(
                                    zone = LandscapeLiveFocusZone.PREVIEW,
                                    action = LandscapeLiveFocusAction.LEFT,
                                )
                                event.isKeyDown(Key.DirectionDown) -> applyFocusAction(
                                    zone = LandscapeLiveFocusZone.PREVIEW,
                                    action = LandscapeLiveFocusAction.DOWN,
                                )
                                else -> false
                            }
                        }
                        .focusGroup(),
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
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(epgFocusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.isKeyDown(Key.DirectionLeft) -> applyFocusAction(
                                    zone = LandscapeLiveFocusZone.EPG,
                                    action = LandscapeLiveFocusAction.LEFT,
                                )
                                event.isKeyDown(Key.DirectionUp) -> applyFocusAction(
                                    zone = LandscapeLiveFocusZone.EPG,
                                    action = LandscapeLiveFocusAction.UP,
                                )
                                else -> false
                            }
                        }
                        .focusGroup(),
                ) {
                    EpgPanel(
                        snapshot = epgSnapshot,
                        loading = epgLoading,
                        failed = epgFailed,
                        onOpenGuide = onOpenEpgGuide,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeBrowseSurface(
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
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.onPreviewKeyEvent(onPreviewKeyEvent),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        HierarchicalLiveBrowse(
            state = state,
            hierarchyLevel = hierarchyLevel,
            playingChannelId = playingChannelId,
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
            focusRequestGeneration = focusRequestGeneration,
            channelFocusRequester = channelFocusRequester,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal enum class LandscapeLiveFocusZone {
    BROWSER,
    PREVIEW,
    EPG,
}

internal enum class LandscapeLiveFocusAction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    BACK,
}

internal object LandscapeLiveFocusPolicy {
    fun consumeBrowserRight(
        isTelevision: Boolean,
        hasPreview: Boolean,
    ): Boolean = isTelevision && hasPreview

    fun destination(
        current: LandscapeLiveFocusZone,
        action: LandscapeLiveFocusAction,
        hasPreview: Boolean,
    ): LandscapeLiveFocusZone? = when (current) {
        LandscapeLiveFocusZone.BROWSER -> when (action) {
            LandscapeLiveFocusAction.RIGHT -> if (hasPreview) LandscapeLiveFocusZone.PREVIEW else null
            else -> null
        }
        LandscapeLiveFocusZone.PREVIEW -> when (action) {
            LandscapeLiveFocusAction.LEFT,
            LandscapeLiveFocusAction.BACK,
            -> LandscapeLiveFocusZone.BROWSER

            LandscapeLiveFocusAction.DOWN -> LandscapeLiveFocusZone.EPG
            else -> null
        }
        LandscapeLiveFocusZone.EPG -> when (action) {
            LandscapeLiveFocusAction.LEFT,
            LandscapeLiveFocusAction.BACK,
            -> LandscapeLiveFocusZone.BROWSER

            LandscapeLiveFocusAction.UP -> LandscapeLiveFocusZone.PREVIEW
            else -> null
        }
    }
}

private fun KeyEvent.isKeyDown(expected: Key): Boolean =
    type == KeyEventType.KeyDown && key == expected
