package app.ownplay.player.download

internal enum class OfflineDownloadFailureDisposition {
    RETRY,
    FAIL,
}

internal object OfflineDownloadRetryPolicy {
    fun forHttpStatus(statusCode: Int): OfflineDownloadFailureDisposition = when (statusCode) {
        408,
        425,
        429,
        500,
        502,
        503,
        504,
        -> OfflineDownloadFailureDisposition.RETRY
        else -> OfflineDownloadFailureDisposition.FAIL
    }
}
