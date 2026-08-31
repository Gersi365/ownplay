package app.ownplay.player.ui

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val controls = PlaybackPresentationPolicy.controlsFor(state)

    BackHandler {
        onClose()
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
                .clickable(onClick = onOpenFullscreen),
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
                    Text(
                        text = playbackStatusLabel(state),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = selection.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playbackStatusLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                )
            }
        }
    }
}
