package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun preservesKnownDurationWhenCurrentCallbackHasNoDuration() {
        val progress = PlaybackProgressPolicy.normalize(
            positionMs = 57_000L,
            durationMs = null,
            fallbackDurationMs = 60_000L,
        )

        assertEquals(57_000L, progress.positionMs)
        assertEquals(60_000L, progress.durationMs)
        assertTrue(progress.completed)
    }

    @Test
    fun currentValidDurationWinsOverFallback() {
        val progress = PlaybackProgressPolicy.normalize(
            positionMs = 50_000L,
            durationMs = 100_000L,
            fallbackDurationMs = 60_000L,
        )

        assertEquals(100_000L, progress.durationMs)
        assertFalse(progress.completed)
    }

    @Test
    fun invalidDurationsDoNotCreateFalseCompletion() {
        val progress = PlaybackProgressPolicy.normalize(
            positionMs = -5L,
            durationMs = 0L,
            fallbackDurationMs = -1L,
        )

        assertEquals(0L, progress.positionMs)
        assertEquals(null, progress.durationMs)
        assertFalse(progress.completed)
    }

    @Test
    fun completionBoundaryIsNinetyFivePercent() {
        assertFalse(
            PlaybackProgressPolicy.normalize(
                positionMs = 94_999L,
                durationMs = 100_000L,
            ).completed,
        )
        assertTrue(
            PlaybackProgressPolicy.normalize(
                positionMs = 95_000L,
                durationMs = 100_000L,
            ).completed,
        )
    }
}
