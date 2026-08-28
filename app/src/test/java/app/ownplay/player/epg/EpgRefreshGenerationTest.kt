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
    fun staleSnapshotDoesNotRunPublishAction() {
        val generation = EpgRefreshGeneration()
        val snapshot = generation.snapshot("source-a")
        var published = false

        generation.invalidate("source-a")
        val accepted = generation.runIfCurrent("source-a", snapshot) {
            published = true
        }

        assertFalse(accepted)
        assertFalse(published)
    }

    @Test
    fun currentSnapshotRunsPublishAction() {
        val generation = EpgRefreshGeneration()
        val snapshot = generation.snapshot("source-a")
        var published = false

        val accepted = generation.runIfCurrent("source-a", snapshot) {
            published = true
        }

        assertTrue(accepted)
        assertTrue(published)
    }

    @Test
    fun invalidatingOneSourceDoesNotInvalidateAnother() {
        val generation = EpgRefreshGeneration()
        val otherSnapshot = generation.snapshot("source-b")

        generation.invalidate("source-a")

        assertTrue(generation.isCurrent("source-b", otherSnapshot))
    }
}
