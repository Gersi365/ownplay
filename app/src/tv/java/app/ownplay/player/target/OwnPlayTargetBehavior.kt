package app.ownplay.player.target

import android.os.SystemClock
import android.view.KeyEvent
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.tv.TvBackgroundPlaybackAction
import app.ownplay.player.ui.tv.TvPlaybackLifecyclePolicy
import app.ownplay.player.ui.tv.TvRemoteActionGuard
import app.ownplay.player.ui.tv.TvRemoteActionKind
import app.ownplay.player.ui.tv.TvRemoteKeySuppression

internal class OwnPlayTargetBehavior {
    private val remoteActionGuard = TvRemoteActionGuard()
    private val remoteKeySuppression = TvRemoteKeySuppression()

    fun handleRemoteKeyEvent(event: KeyEvent): Boolean {
        if (!event.isRemoteActivationKey()) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    remoteKeySuppression.suppress(event.keyCode)
                    true
                } else if (
                    !remoteActionGuard.tryAcquire(
                        nowMillis = SystemClock.elapsedRealtime(),
                        actionId = event.keyCode,
                    )
                ) {
                    remoteKeySuppression.suppress(event.keyCode)
                    true
                } else {
                    remoteKeySuppression.allow(event.keyCode)
                    false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (remoteActionGuard.isGloballyBlocked(SystemClock.elapsedRealtime())) {
                    remoteKeySuppression.consumeRelease(event.keyCode)
                    true
                } else {
                    remoteKeySuppression.consumeRelease(event.keyCode)
                }
            }
            else -> false
        }
    }

    fun holdTransitionLock() {
        remoteActionGuard.extendBlock(
            nowMillis = SystemClock.elapsedRealtime(),
            kind = TvRemoteActionKind.TRANSITION,
        )
    }

    fun shouldSuspendPlaybackOnBackground(state: PlaybackState): Boolean =
        TvPlaybackLifecyclePolicy.backgroundAction(state) == TvBackgroundPlaybackAction.SUSPEND
}

private fun KeyEvent.isRemoteActivationKey(): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
        keyCode == KeyEvent.KEYCODE_BACK
