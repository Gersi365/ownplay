package app.ownplay.player.live.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackLocatorDescriptorTest {
    @Test
    fun xtreamLiveRequiresPositiveStreamId() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackLocatorDescriptor.xtreamLive(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackLocatorDescriptor.xtreamLive(-1)
        }
    }

    @Test
    fun xtreamLiveEncodesPositiveStreamId() {
        assertEquals(
            "ownplay-locator-v1|xtream-live|42",
            PlaybackLocatorDescriptor.xtreamLive(42),
        )
    }
}
