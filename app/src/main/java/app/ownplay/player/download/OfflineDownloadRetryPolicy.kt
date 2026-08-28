package app.ownplay.player.download

internal fun isRetryableDownloadHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 429 ||
        statusCode in 500..599
