package app.ownplay.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadRangePolicyTest {
    @Test
    fun matchingPartialContentRangeAppendsFromExistingOffset() {
        assertEquals(
            OfflineDownloadResponseMode.APPEND,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 206,
                contentRangeHeader = "bytes 1024-2047/4096",
            ),
        )
    }

    @Test
    fun fullResponseReplacesPartialWhenServerIgnoresRangeRequest() {
        assertEquals(
            OfflineDownloadResponseMode.REPLACE,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 200,
                contentRangeHeader = null,
            ),
        )
    }

    @Test
    fun partialResponseStartingAtZeroCanReplaceEmptyDestination() {
        assertEquals(
            OfflineDownloadResponseMode.REPLACE,
            offlineDownloadResponseMode(
                existingBytes = 0L,
                responseCode = 206,
                contentRangeHeader = "bytes 0-1023/4096",
            ),
        )
    }

    @Test
    fun mismatchedOrMissingContentRangeRequiresCleanRetry() {
        assertEquals(
            OfflineDownloadResponseMode.RETRY_FROM_ZERO,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 206,
                contentRangeHeader = "bytes 0-1023/4096",
            ),
        )
        assertEquals(
            OfflineDownloadResponseMode.RETRY_FROM_ZERO,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 206,
                contentRangeHeader = null,
            ),
        )
    }

    @Test
    fun malformedContentRangeRequiresCleanRetry() {
        assertEquals(
            OfflineDownloadResponseMode.RETRY_FROM_ZERO,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 206,
                contentRangeHeader = "bytes 2048-1024/4096",
            ),
        )
        assertEquals(
            OfflineDownloadResponseMode.RETRY_FROM_ZERO,
            offlineDownloadResponseMode(
                existingBytes = 1_024L,
                responseCode = 206,
                contentRangeHeader = "bytes 1024-2047/2000",
            ),
        )
        assertEquals(
            OfflineDownloadResponseMode.RETRY_FROM_ZERO,
            offlineDownloadResponseMode(
                existingBytes = 0L,
                responseCode = 206,
                contentRangeHeader = "not-a-range",
            ),
        )
    }

    @Test
    fun rangeNotSatisfiableRestartsOnlyWhenThereIsPartialData() {
        assertTrue(shouldRestartOfflineDownloadFromZero(existingBytes = 1_024L, responseCode = 416))
        assertFalse(shouldRestartOfflineDownloadFromZero(existingBytes = 0L, responseCode = 416))
        assertFalse(shouldRestartOfflineDownloadFromZero(existingBytes = 1_024L, responseCode = 500))
    }

    @Test
    fun knownTotalAddsResumeOffsetWithoutOverflow() {
        assertEquals(4_096L, offlineDownloadTotalBytes(startBytes = 1_024L, bodyLength = 3_072L))
        assertEquals(Long.MAX_VALUE, offlineDownloadTotalBytes(Long.MAX_VALUE - 1L, 1L))
        assertNull(offlineDownloadTotalBytes(Long.MAX_VALUE, 1L))
        assertNull(offlineDownloadTotalBytes(-1L, 1L))
        assertNull(offlineDownloadTotalBytes(0L, -1L))
    }
}
