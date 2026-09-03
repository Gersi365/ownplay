package app.ownplay.player.ui

import app.ownplay.player.epg.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveFullscreenEpgProgressTest {
    @Test
    fun progressTracksCurrentPositionAndClampsToProgramBounds() {
        val program = epgProgram(start = 100L, end = 200L)

        assertEquals(0f, fullscreenEpgProgress(program, 50L)!!, 0.0001f)
        assertEquals(0.5f, fullscreenEpgProgress(program, 150L)!!, 0.0001f)
        assertEquals(1f, fullscreenEpgProgress(program, 250L)!!, 0.0001f)
    }

    @Test
    fun progressIsUnavailableWithoutUsableTimeBounds() {
        assertNull(fullscreenEpgProgress(epgProgram(start = null, end = 200L), 150L))
        assertNull(fullscreenEpgProgress(epgProgram(start = 100L, end = null), 150L))
        assertNull(fullscreenEpgProgress(epgProgram(start = 200L, end = 100L), 150L))
        assertNull(fullscreenEpgProgress(epgProgram(start = 100L, end = 100L), 100L))
    }

    @Test
    fun epgRefreshUsesNormalIntervalWithoutCurrentProgramBoundary() {
        assertEquals(30_000L, fullscreenEpgRefreshDelayMillis(null, nowEpochSeconds = 100L))
        assertEquals(
            30_000L,
            fullscreenEpgRefreshDelayMillis(
                currentProgramEndEpochSeconds = 200L,
                nowEpochSeconds = 100L,
            ),
        )
    }

    @Test
    fun epgRefreshMovesCloseToUpcomingProgramBoundary() {
        assertEquals(
            5_250L,
            fullscreenEpgRefreshDelayMillis(
                currentProgramEndEpochSeconds = 105L,
                nowEpochSeconds = 100L,
            ),
        )
        assertEquals(
            1_250L,
            fullscreenEpgRefreshDelayMillis(
                currentProgramEndEpochSeconds = 101L,
                nowEpochSeconds = 100L,
            ),
        )
    }

    @Test
    fun expiredProgramBoundaryRetriesSoonWithoutTightLoop() {
        assertEquals(
            1_000L,
            fullscreenEpgRefreshDelayMillis(
                currentProgramEndEpochSeconds = 100L,
                nowEpochSeconds = 100L,
            ),
        )
        assertEquals(
            1_000L,
            fullscreenEpgRefreshDelayMillis(
                currentProgramEndEpochSeconds = 99L,
                nowEpochSeconds = 100L,
            ),
        )
    }

    private fun epgProgram(start: Long?, end: Long?) = EpgProgram(
        title = "Programme",
        description = null,
        startEpochSeconds = start,
        endEpochSeconds = end,
        startLabel = null,
        endLabel = null,
    )
}
