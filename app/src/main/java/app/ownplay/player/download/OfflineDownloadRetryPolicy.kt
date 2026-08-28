package app.ownplay.player.download

internal enum class OfflineDownloadFailureDisposition {
    RETRY,
    FAIL,
}

internal object OfflineDownloadRetryPolicy {
    fun forHttpStatus(statusCode: Int): OfflineDownloadFailureDisposition = when {
        statusCode == 408 -> OfflineDownloadFailureDisposition.RETRY
        statusCode == 429 -> OfflineDownloadFailureDisposition.RETRY
        statusCode in 500..599 -> OfflineDownloadFailureDisposition.RETRY
        else -> OfflineDownloadFailureDisposition.FAIL
    }
}
