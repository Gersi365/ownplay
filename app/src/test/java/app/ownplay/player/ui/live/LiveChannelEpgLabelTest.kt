package app.ownplay.player.ui.live

import app.ownplay.player.epg.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChannelEpgLabelTest {
    @Test
    fun listAndCardLabelsShowNowWithFullTimeRange() {
        assertEquals(
            "Now · 10:00–11:00 · Morning News",
            liveCurrentProgramLabel(
                program = program(start = "10:00", end = "11:00"),
                compact = false,
            ),
        )
    }

    @Test
    fun compactLabelsKeepOnlyTimeAndProgramme() {
        assertEquals(
            "10:00–11:00 · Morning News",
            liveCurrentProgramLabel(
                program = program(start = "10:00", end = "11:00"),
                compact = true,
            ),
        )
    }

    @Test
    fun labelsRemainUsefulWithPartialOrMissingTime() {
        assertEquals(
            "Now · 10:00 · Morning News",
            liveCurrentProgramLabel(program(start = "10:00", end = null), compact = false),
        )
        assertEquals(
            "11:00 · Morning News",
            liveCurrentProgramLabel(program(start = null, end = "11:00"), compact = true),
        )
        assertEquals(
            "Morning News",
            liveCurrentProgramLabel(program(start = null, end = null), compact = true),
        )
    }

    private fun program(start: String?, end: String?) = EpgProgram(
        title = "Morning News",
        description = null,
        startEpochSeconds = null,
        endEpochSeconds = null,
        startLabel = start,
        endLabel = end,
    )
}
