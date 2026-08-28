package app.ownplay.player.download

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineDownloadRetryPolicyTest {
    @Test
    fun retriesTransientHttpFailures() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { statusCode ->
            assertEquals(
                "Expected HTTP $statusCode to be retryable",
                OfflineDownloadFailureDisposition.RETRY,
                OfflineDownloadRetryPolicy.forHttpStatus(statusCode),
            )
        }
    }

    @Test
    fun failsTerminalHttpErrorsWithoutAutomaticRetry() {
        listOf(400, 401, 403, 404, 405, 409, 410, 416, 501, 505, 599).forEach { statusCode ->
            assertEquals(
                "Expected HTTP $statusCode to be terminal",
                OfflineDownloadFailureDisposition.FAIL,
                OfflineDownloadRetryPolicy.forHttpStatus(statusCode),
            )
        }
    }
}
