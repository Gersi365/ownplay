package app.ownplay.player.ui

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
 * Live preview is intentionally presentation-only.
 *
 * Channel selection opens the preview. No playback buttons are rendered inside it: TV users
 * can press OK/Enter while the preview is focused to open the full player, touch users can tap
 * the video, and Back/ESC closes the preview. EPG remains a separate surface owned by LiveRoute.
 */
@OptIn(UnstableApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
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

    BackHandler(enabled = true, onBack = onClose)

    val interactionModifier = if (isTelevision) {
        Modifier
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != AndroidKeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (native.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            onOpenFullscreen()
                            true
                        }

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        -> {
                            onClose()
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusable()
    } else {
        Modifier.clickable(onClick = onOpenFullscreen)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .then(interactionModifier),
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

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
            ) {
                Text(
                    text = "LIVE",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (controls.showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (state is PlaybackState.Failed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = playbackStatusLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
