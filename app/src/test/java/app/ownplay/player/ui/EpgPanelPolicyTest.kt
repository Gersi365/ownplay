package app.ownplay.player.ui

import app.ownplay.player.epg.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgPanelPolicyTest {
    @Test
    fun `current program progress is normalized inside program window`() {
        val program = program(start = 1_000L, end = 1_600L)

        assertEquals(0.0f, programProgressFraction(program, 1_000L)!!, 0.0001f)
        assertEquals(0.5f, programProgressFraction(program, 1_300L)!!, 0.0001f)
        assertEquals(1.0f, programProgressFraction(program, 1_600L)!!, 0.0001f)
    }

    @Test
    fun `progress is hidden outside program window`() {
        val program = program(start = 1_000L, end = 1_600L)

        assertNull(programProgressFraction(program, 999L))
        assertNull(programProgressFraction(program, 1_601L))
    }

    @Test
    fun `progress requires valid start and end timestamps`() {
        assertNull(programProgressFraction(program(start = null, end = 1_600L), 1_300L))
        assertNull(programProgressFraction(program(start = 1_000L, end = null), 1_300L))
        assertNull(programProgressFraction(program(start = 1_600L, end = 1_000L), 1_300L))
    }

    private fun program(start: Long?, end: Long?): EpgProgram = EpgProgram(
        title = "Program",
        description = null,
        startEpochSeconds = start,
        endEpochSeconds = end,
        startLabel = null,
        endLabel = null,
    )
}
