package app.ownplay.player.epg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgRefreshGenerationTest {
    @Test
    fun invalidationRejectsOlderRefreshSnapshot() {
        val generation = EpgRefreshGeneration()
        val snapshot = generation.snapshot("source-a")

        generation.invalidate("source-a")

        assertFalse(generation.isCurrent("source-a", snapshot))
        assertTrue(generation.isCurrent("source-a", generation.snapshot("source-a")))
    }

    @Test
    fun invalidatingOneSourceDoesNotInvalidateAnother() {
        val generation = EpgRefreshGeneration()
        val otherSnapshot = generation.snapshot("source-b")

        generation.invalidate("source-a")

        assertTrue(generation.isCurrent("source-b", otherSnapshot))
    }
}
