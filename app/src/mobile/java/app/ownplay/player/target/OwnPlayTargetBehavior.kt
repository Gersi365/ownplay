package app.ownplay.player.target

import android.view.KeyEvent
import app.ownplay.player.playback.PlaybackState

internal class OwnPlayTargetBehavior {
    fun handleRemoteKeyEvent(event: KeyEvent): Boolean = false

    fun holdTransitionLock() = Unit

    fun shouldSuspendPlaybackOnBackground(state: PlaybackState): Boolean = false
}
