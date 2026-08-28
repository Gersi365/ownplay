package app.ownplay.player.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadRetryPolicyTest {
    @Test
    fun `retries transient HTTP failures`() {
        listOf(408, 429, 500, 502, 503, 599).forEach { status ->
            assertTrue("Expected $status to be retryable", isRetryableDownloadHttpStatus(status))
        }
    }

    @Test
    fun `does not retry permanent HTTP failures`() {
        listOf(400, 401, 403, 404, 409, 410, 422).forEach { status ->
            assertFalse("Expected $status to be permanent", isRetryableDownloadHttpStatus(status))
        }
    }

    @Test
    fun `automatic retry budget is bounded`() {
        assertTrue(shouldRetryDownload(runAttemptCount = 0, retryableFailure = true))
        assertTrue(shouldRetryDownload(runAttemptCount = 2, retryableFailure = true))
        assertFalse(shouldRetryDownload(runAttemptCount = 3, retryableFailure = true))
        assertFalse(shouldRetryDownload(runAttemptCount = 0, retryableFailure = false))
    }
}
