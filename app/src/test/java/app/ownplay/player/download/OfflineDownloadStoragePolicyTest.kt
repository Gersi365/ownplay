package app.ownplay.player.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadStoragePolicyTest {
    @Test
    fun acceptsLargeDownloadWhenReserveStillRemains() {
        val gib = 1024L * 1024L * 1024L
        assertTrue(
            hasEnoughOfflineDownloadSpace(
                usableSpaceBytes = 16L * gib,
                requiredBytes = 12L * gib,
            ),
        )
    }

    @Test
    fun rejectsLargeDownloadWhenItWouldConsumeReserve() {
        val gib = 1024L * 1024L * 1024L
        assertFalse(
            hasEnoughOfflineDownloadSpace(
                usableSpaceBytes = 12L * gib + 128L * 1024L * 1024L,
                requiredBytes = 12L * gib,
            ),
        )
    }

    @Test
    fun rejectsWhenFreeSpaceIsAlreadyBelowReserve() {
        assertFalse(
            hasEnoughOfflineDownloadSpace(
                usableSpaceBytes = OFFLINE_DOWNLOAD_FREE_SPACE_RESERVE_BYTES - 1L,
                requiredBytes = 0L,
            ),
        )
    }
}
