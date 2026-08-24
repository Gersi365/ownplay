package app.ownplay.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference

private const val DEFAULT_CONTROLLER_TIMEOUT_MILLIS = 3_000

/**
 * Narrow UI bridge for interactions that must work consistently across media surfaces.
 *
 * It does not own playback and does not create another player. The currently bound PlayerView
 * remains attached to the single Media3PlaybackEngine through PlaybackVideoOutput.
 */
@OptIn(UnstableApi::class)
object PlaybackInteractionBridge {
    private var boundView = WeakReference<PlayerView>(null)
    private var backOwner: Any? = null
    private var backAction: (() -> Unit)? = null

    fun bind(
        output: PlaybackVideoOutput,
        view: PlayerView,
        showNativeController: Boolean = false,
    ) {
        output.bind(view)
        observeBoundView(view)
        if (showNativeController) {
            view.post {
                if (boundView.get() !== view) return@post
                view.useController = true
                view.controllerShowTimeoutMs = DEFAULT_CONTROLLER_TIMEOUT_MILLIS
                view.setControllerAutoShow(true)
                view.showController()
            }
        }
    }

    fun unbind(
        output: PlaybackVideoOutput,
        view: PlayerView,
    ) {
        output.unbind(view)
        observeUnboundView(view)
    }

    fun observeBoundView(view: PlayerView) {
        boundView = WeakReference(view)
    }

    fun observeUnboundView(view: PlayerView) {
        if (boundView.get() === view) {
            boundView.clear()
        }
    }

    fun seekBy(deltaMillis: Long): Boolean {
        if (deltaMillis == 0L) return false
        val player = boundView.get()?.player ?: return false
        val current = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        val unclamped = current + deltaMillis
        val target = if (duration != C.TIME_UNSET && duration > 0L) {
            unclamped.coerceIn(0L, duration)
        } else {
            unclamped.coerceAtLeast(0L)
        }
        player.seekTo(target)
        return true
    }

    fun registerBackAction(
        owner: Any,
        action: () -> Unit,
    ) {
        backOwner = owner
        backAction = action
    }

    fun clearBackAction(owner: Any) {
        if (backOwner === owner) {
            backOwner = null
            backAction = null
        }
    }

    fun handleBack(): Boolean {
        val action = backAction ?: return false
        action()
        return true
    }
}
