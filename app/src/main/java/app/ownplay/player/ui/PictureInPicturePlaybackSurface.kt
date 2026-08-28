package app.ownplay.player.ui

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackVideoOutput
import java.lang.ref.WeakReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun PictureInPicturePlaybackSurface(
    videoOutput: PlaybackVideoOutput,
    onProgress: ((positionMs: Long, durationMs: Long?) -> Unit)? = null,
) {
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var returnTarget by remember { mutableStateOf<WeakReference<PlayerView>?>(null) }
    val progressCallback by rememberUpdatedState(onProgress)

    fun reportProgress(view: PlayerView?) {
        val player = view?.player ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        if (positionMs <= 0L) return
        progressCallback?.invoke(
            positionMs,
            player.duration.takeIf { it > 0L },
        )
    }

    LaunchedEffect(playerView) {
        while (currentCoroutineContext().isActive) {
            delay(5_000L)
            reportProgress(playerView)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        AndroidView(
            factory = { context ->
                val previousView = PlaybackInteractionBridge.currentBoundView()
                PlayerView(context).apply {
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    returnTarget = previousView?.let(::WeakReference)
                    videoOutput.bind(this)
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.useController = false
                playerView = view
            },
            onRelease = { view ->
                reportProgress(view)
                val target = returnTarget
                    ?.get()
                    ?.takeIf { candidate -> candidate.isAttachedToWindow }
                if (target != null) {
                    videoOutput.bind(target)
                } else {
                    videoOutput.unbind(view)
                }
                returnTarget = null
                if (playerView === view) playerView = null
            },
        )
    }
}
