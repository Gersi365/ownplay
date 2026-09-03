package app.ownplay.player.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpgCurrentProgramIndexTest {
    @Test
    fun currentProgramMapsToEveryChannelSharingTheSameEpgId() {
        val current = program("Current", 100, 200)

        val result = EpgCurrentProgramIndex.currentByChannel(
            channelIdsByEpgChannelId = mapOf("epg-1" to listOf("channel-a", "channel-b")),
            programsByEpgChannelId = mapOf("epg-1" to listOf(current)),
            nowEpochSeconds = 150,
        )

        assertSame(current, result["channel-a"])
        assertSame(current, result["channel-b"])
    }

    @Test
    fun overlappingProgramsPreferTheOneThatStartedMostRecently() {
        val older = program("Older overlap", 100, 300)
        val newer = program("Newer overlap", 200, 280)

        assertSame(
            newer,
            EpgCurrentProgramIndex.currentProgram(
                programs = listOf(older, newer),
                nowEpochSeconds = 250,
            ),
        )
    }

    @Test
    fun endBoundaryFutureAndIncompleteProgramsAreNotCurrent() {
        val result = EpgCurrentProgramIndex.currentByChannel(
            channelIdsByEpgChannelId = mapOf(
                "ended" to listOf("channel-ended"),
                "future" to listOf("channel-future"),
                "incomplete" to listOf("channel-incomplete"),
                "active" to listOf("channel-active"),
            ),
            programsByEpgChannelId = mapOf(
                "ended" to listOf(program("Ended", 100, 150)),
                "future" to listOf(program("Future", 151, 200)),
                "incomplete" to listOf(program("Incomplete", null, 200)),
                "active" to listOf(program("Active", 150, 151)),
            ),
            nowEpochSeconds = 150,
        )

        assertFalse(result.containsKey("channel-ended"))
        assertFalse(result.containsKey("channel-future"))
        assertFalse(result.containsKey("channel-incomplete"))
        assertEquals("Active", result["channel-active"]?.title)
    }

    @Test
    fun directCurrentProgramLookupUsesTheSameHalfOpenBoundary() {
        val current = program("Current", 100, 200)
        val next = program("Next", 200, 300)

        assertSame(
            current,
            EpgCurrentProgramIndex.currentProgram(
                programs = listOf(current, next),
                nowEpochSeconds = 199,
            ),
        )
        assertSame(
            next,
            EpgCurrentProgramIndex.currentProgram(
                programs = listOf(current, next),
                nowEpochSeconds = 200,
            ),
        )
    }

    @Test
    fun preparedLookupMatchesGenericSelectionAcrossOverlapAndBoundaries() {
        val programs = EpgTimelineProjector.normalize(
            listOf(
                program("Long running", 100, 500),
                program("Short overlap", 200, 250),
                program("Later", 500, 600),
                program("Incomplete end", 300, null),
                program("Incomplete start", null, 900),
            ),
        )
        val prepared = EpgCurrentProgramIndex.prepareNormalized(programs)

        listOf(99L, 100L, 225L, 250L, 300L, 499L, 500L, 600L).forEach { now ->
            assertSame(
                EpgCurrentProgramIndex.currentProgram(programs, now),
                prepared.currentProgram(now),
            )
        }
    }

    @Test
    fun preparedLookupFindsOlderLongRunningProgramAfterLaterOverlapEnded() {
        val longRunning = program("Long running", 100, 1_000)
        val endedOverlap = program("Ended overlap", 900, 920)
        val programs = EpgTimelineProjector.normalize(listOf(longRunning, endedOverlap))
        val prepared = EpgCurrentProgramIndex.prepareNormalized(programs)

        assertSame(longRunning, prepared.currentProgram(950))
    }

    @Test
    fun preparedLookupPreservesLatestStartEndAndTitleTieBreakers() {
        val shorter = program("Z shorter", 100, 200)
        val alpha = program("Alpha", 100, 300)
        val omega = program("Omega", 100, 300)
        val programs = EpgTimelineProjector.normalize(listOf(omega, shorter, alpha))
        val prepared = EpgCurrentProgramIndex.prepareNormalized(programs)

        assertSame(omega, prepared.currentProgram(150))
    }

    @Test
    fun preparedChannelLookupMapsSharedEpgIdsWithoutRescanningProgramLists() {
        val current = program("Current", 10_000, 20_000)
        val programs = EpgTimelineProjector.normalize(
            (0 until 5_000).map { index ->
                val start = index.toLong() * 10L
                program("Program $index", start, start + 10L)
            } + current,
        )
        val prepared = EpgCurrentProgramIndex.prepareNormalized(programs)

        val result = EpgCurrentProgramIndex.currentByChannelPrepared(
            channelIdsByEpgChannelId = mapOf("epg" to listOf("one", "two", "three")),
            preparedByEpgChannelId = mapOf("epg" to prepared),
            nowEpochSeconds = 15_000,
        )

        assertSame(current, result["one"])
        assertSame(current, result["two"])
        assertSame(current, result["three"])
    }

    @Test
    fun preparedLookupReturnsNullWhenNoStartedProgramCanStillBeActive() {
        val programs = EpgTimelineProjector.normalize(
            listOf(
                program("Old", 100, 200),
                program("Older", 10, 50),
                program("Future", 400, 500),
            ),
        )

        assertNull(EpgCurrentProgramIndex.prepareNormalized(programs).currentProgram(300))
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
