package app.ownplay.player.ui.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
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
import app.ownplay.player.ui.view.ContentViewMode

/**
 * Landscape Live shell with one consistent browse model across touch and TV layouts.
 *
 * Before a channel is selected, browsing owns the full workspace. Preview and EPG are not
 * rendered at all. Once a channel is selected, the browse area remains available on the left
 * while the selected channel owns a dedicated playback/EPG panel on the right.
 *
 * Settings deliberately does not live inside the channel workspace. App-level navigation owns
 * that destination instead of consuming permanent channel-list space.
 *
 * D-pad focus is split into three predictable zones: Browser -> Preview -> EPG. The channel
 * browser owns initial focus, Right from channel content enters Preview, Down enters EPG, and
 * Left from either right-side zone returns to the currently active channel. Back/ESC remains the
 * Preview close action owned by LivePreviewPanel. Toolbar, Search and category controls retain
 * their native horizontal traversal. Returning from Full View may explicitly restore Preview
 * focus once. Channel focus requests scroll the active row/card into the viewport first.
 */
@Composable
internal fun LandscapeLiveWorkspaceAdaptive(
    state: LiveBrowseState,
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
    focusPreviewOnEntry: Boolean = false,
    onPreviewEntryFocusRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
        state.channels.firstOrNull()?.channelId,
        preview?.request?.channelId,
        focusPreviewOnEntry,
    ) {
        val currentPreviewChannelId = preview?.request?.channelId
        if (currentPreviewChannelId != null) {
            lastPreviewChannelId = currentPreviewChannelId
        }
        if (!initialBrowserFocusRequested && state.channels.isNotEmpty()) {
            initialBrowserFocusRequested = true
            if (!focusPreviewOnEntry) {
                requestChannelFocus(currentPreviewChannelId ?: state.channels.first().channelId)
            }
        }
        if (currentPreviewChannelId == null && previousPreviewChannelId != null) {
            requestChannelFocus(previousPreviewChannelId)
        }
        previousPreviewChannelId = currentPreviewChannelId
    }

    LaunchedEffect(focusPreviewOnEntry, preview?.request?.channelId) {
        if (!focusPreviewOnEntry || preview == null) return@LaunchedEffect
        withFrameNanos { }
        previewFocusRequester.requestFocus()
        onPreviewEntryFocusRestored()
    }

    if (preview == null) {
        LandscapeBrowseSurface(
            state = state,
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
            onChannelPreviewKeyEvent = { false },
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LandscapeBrowseSurface(
            state = state,
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
            onChannelPreviewKeyEvent = { event ->
                if (event.isKeyDown(Key.DirectionRight)) {
                    applyFocusAction(
                        zone = LandscapeLiveFocusZone.BROWSER,
                        action = LandscapeLiveFocusAction.RIGHT,
                    )
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
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

                HorizontalDivider()

                val guideAvailable = epgSnapshot?.programs?.isNotEmpty() == true && !epgLoading
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
                                event.isConfirmKeyDown() && guideAvailable -> {
                                    onOpenEpgGuide()
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
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
    onChannelPreviewKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp,
    ) {
        PortraitLiveBrowseWithViewModes(
            state = state,
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
            onChannelPreviewKeyEvent = onChannelPreviewKeyEvent,
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
            LandscapeLiveFocusAction.LEFT -> LandscapeLiveFocusZone.BROWSER
            LandscapeLiveFocusAction.DOWN -> LandscapeLiveFocusZone.EPG
            else -> null
        }
        LandscapeLiveFocusZone.EPG -> when (action) {
            LandscapeLiveFocusAction.LEFT -> LandscapeLiveFocusZone.BROWSER
            LandscapeLiveFocusAction.UP -> LandscapeLiveFocusZone.PREVIEW
            else -> null
        }
    }
}

private fun KeyEvent.isKeyDown(expected: Key): Boolean =
    type == KeyEventType.KeyDown && key == expected

private fun KeyEvent.isConfirmKeyDown(): Boolean =
    type == KeyEventType.KeyDown &&
        key in setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)
