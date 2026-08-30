package app.ownplay.player.ui

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackNavigationDirection
import app.ownplay.player.playback.PlaybackPresentationPolicy
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.playback.PlaybackVideoOutput

/**
 * Live Preview is deliberately presentation-only.
 *
 * No playback/navigation/fullscreen/close buttons are rendered on either mobile or TV. TV keeps
 * focus in the channel browser so a second OK on the selected channel can open fullscreen. Mobile
 * gets a transparent tap target above PlayerView so tapping video opens fullscreen without adding a
 * visible control layer. Back/ESC ownership remains in LiveRoute so Preview closes first.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(UnstableApi::class)
@Composable
internal fun LivePreviewPanel(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    videoOutput: PlaybackVideoOutput,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (PlaybackNavigationDirection) -> Unit,
    onOpenFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val controls = PlaybackPresentationPolicy.controlsFor(state)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black,
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        videoOutput.bind(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { view -> videoOutput.unbind(view) },
            )

            if (!isTelevision) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onOpenFullscreen,
                        ),
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f),
            ) {
                Text(
                    text = "LIVE",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (controls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (state is PlaybackState.Failed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = playbackStatusLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
