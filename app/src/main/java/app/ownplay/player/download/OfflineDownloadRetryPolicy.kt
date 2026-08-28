package app.ownplay.player.download

internal const val MAX_AUTOMATIC_DOWNLOAD_RETRIES = 3

internal fun isRetryableDownloadHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 429 ||
        statusCode in 500..599

internal fun shouldRetryDownload(
    runAttemptCount: Int,
    retryableFailure: Boolean,
): Boolean = retryableFailure && runAttemptCount < MAX_AUTOMATIC_DOWNLOAD_RETRIES
