package app.ownplay.player.ui.live

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier,
) {
    val browseContent: @Composable (Modifier) -> Unit = { browseModifier ->
        Surface(
            modifier = browseModifier,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp,
        ) {
            PortraitLiveBrowseWithViewModes(
                state = state,
                playingChannelId = preview?.request?.channelId,
                currentEpgByChannelId = currentEpgByChannelId,
                viewMode = viewMode,
                onViewModeSelected = onViewModeSelected,
                onSearchChange = onSearchChange,
                onCategorySelected = onCategorySelected,
                onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                onOrderChanged = onOrderChanged,
                onCustomGroupSelected = onCustomGroupSelected,
                onChannelSelected = onChannelSelected,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (preview == null) {
        browseContent(
            modifier
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
        browseContent(
            Modifier
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
                    loading = epgLoading,
                    failed = epgFailed,
                    onOpenGuide = onOpenEpgGuide,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
