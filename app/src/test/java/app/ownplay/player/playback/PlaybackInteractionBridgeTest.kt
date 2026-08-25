package app.ownplay.player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackInteractionBridgeTest {
    @Test
    fun registeredBackActionIsConsumedUntilOwnerClearsIt() {
        val owner = Any()
        var invoked = false

        PlaybackInteractionBridge.registerBackAction(owner) {
            invoked = true
        }

        assertTrue(PlaybackInteractionBridge.handleBack())
        assertTrue(invoked)

        PlaybackInteractionBridge.clearBackAction(owner)
        assertFalse(PlaybackInteractionBridge.handleBack())
    }
}
