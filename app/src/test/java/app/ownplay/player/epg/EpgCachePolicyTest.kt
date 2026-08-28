package app.ownplay.player.epg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgCachePolicyTest {
    @Test
    fun entryIsFreshOnlyInsidePositiveTtlWindow() {
        assertTrue(
            isEpgCacheFresh(
                loadedAtEpochSeconds = 1_000L,
                nowEpochSeconds = 1_299L,
                ttlSeconds = 300L,
            ),
        )
        assertFalse(
            isEpgCacheFresh(
                loadedAtEpochSeconds = 1_000L,
                nowEpochSeconds = 1_300L,
                ttlSeconds = 300L,
            ),
        )
    }

    @Test
    fun clockRollbackMakesEntryStale() {
        assertFalse(
            isEpgCacheFresh(
                loadedAtEpochSeconds = 1_000L,
                nowEpochSeconds = 999L,
                ttlSeconds = 300L,
            ),
        )
    }

    @Test
    fun invalidTimestampsOrTtlAreNeverFresh() {
        assertFalse(isEpgCacheFresh(-1L, 1_000L, 300L))
        assertFalse(isEpgCacheFresh(1_000L, 1_000L, 0L))
        assertFalse(isEpgCacheFresh(1_000L, 1_000L, -1L))
    }
}
