package app.ownplay.player.ui

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.ownplay.player.playback.PlaybackVideoOutput

@OptIn(UnstableApi::class)
@Composable
fun PictureInPicturePlaybackSurface(
    videoOutput: PlaybackVideoOutput,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    videoOutput.bind(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.useController = false },
            onRelease = { view -> videoOutput.unbind(view) },
        )
    }
}
