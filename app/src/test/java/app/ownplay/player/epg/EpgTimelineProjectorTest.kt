package app.ownplay.player.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgTimelineProjectorTest {
    @Test
    fun outOfOrderProgramsAreSortedDeduplicatedAndClassified() {
        val duplicatePast = program("Past", 100, 200)
        val current = program("Current", 200, 300)
        val future = program("Future", 300, 400)

        val timeline = EpgTimelineProjector.project(
            programs = listOf(future, current, duplicatePast, duplicatePast),
            nowEpochSeconds = 250,
        )

        assertEquals(listOf("Past", "Current", "Future"), timeline.programs.map { it.title })
        assertEquals(listOf("Past"), timeline.past.map { it.title })
        assertEquals("Current", timeline.current?.title)
        assertEquals(listOf("Future"), timeline.future.map { it.title })
    }

    @Test
    fun gapBetweenProgramsHasNoFalseCurrentProgram() {
        val timeline = EpgTimelineProjector.project(
            programs = listOf(
                program("Past", 100, 150),
                program("Future", 300, 350),
            ),
            nowEpochSeconds = 200,
        )

        assertNull(timeline.current)
        assertEquals(listOf("Past"), timeline.past.map { it.title })
        assertEquals(listOf("Future"), timeline.future.map { it.title })
    }

    @Test
    fun reversedOrZeroLengthIntervalsAreExcluded() {
        val timeline = EpgTimelineProjector.project(
            programs = listOf(
                program("Reversed", 300, 100),
                program("Zero length", 200, 200),
                program("Valid", 200, 300),
                program("Start only", 400, null),
            ),
            nowEpochSeconds = 250,
        )

        assertEquals(listOf("Valid", "Start only"), timeline.programs.map { it.title })
        assertEquals("Valid", timeline.current?.title)
        assertEquals(emptyList<String>(), timeline.past.map { it.title })
        assertEquals(listOf("Start only"), timeline.future.map { it.title })
    }

    private fun program(
        title: String,
        start: Long?,
        end: Long?,
    ) = EpgProgram(
        title = title,
        description = null,
        startEpochSeconds = start,
        endEpochSeconds = end,
        startLabel = start?.toString(),
        endLabel = end?.toString(),
    )
}
