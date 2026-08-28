package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun preservesExistingDurationWhenCurrentTickHasNoDuration() {
        val normalized = normalizePlaybackProgress(
            positionMs = 30_000L,
            reportedDurationMs = null,
            existingDurationMs = 120_000L,
        )

        assertEquals(30_000L, normalized.positionMs)
        assertEquals(120_000L, normalized.durationMs)
        assertFalse(normalized.completed)
    }

    @Test
    fun reportedDurationOverridesPreviouslyStoredDuration() {
        val normalized = normalizePlaybackProgress(
            positionMs = 114_000L,
            reportedDurationMs = 120_000L,
            existingDurationMs = 100_000L,
        )

        assertEquals(120_000L, normalized.durationMs)
        assertTrue(normalized.completed)
    }

    @Test
    fun invalidDurationsDoNotProduceFalseCompletion() {
        val normalized = normalizePlaybackProgress(
            positionMs = 50_000L,
            reportedDurationMs = 0L,
            existingDurationMs = -1L,
        )

        assertEquals(null, normalized.durationMs)
        assertFalse(normalized.completed)
    }

    @Test
    fun negativePositionIsClampedToZero() {
        val normalized = normalizePlaybackProgress(
            positionMs = -500L,
            reportedDurationMs = 100_000L,
        )

        assertEquals(0L, normalized.positionMs)
        assertFalse(normalized.completed)
    }

    @Test
    fun completionStartsAtExactlyNinetyFivePercent() {
        val beforeThreshold = normalizePlaybackProgress(
            positionMs = 94_999L,
            reportedDurationMs = 100_000L,
        )
        val atThreshold = normalizePlaybackProgress(
            positionMs = 95_000L,
            reportedDurationMs = 100_000L,
        )

        assertFalse(beforeThreshold.completed)
        assertTrue(atThreshold.completed)
    }
}
