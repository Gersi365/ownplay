package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInteractionBridgePolicyTest {
    @Test
    fun doubleTapSeekDistanceIsTenSeconds() {
        assertEquals(10_000L, PLAYBACK_DOUBLE_TAP_SEEK_MILLIS)
    }
}
