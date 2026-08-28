package app.ownplay.player.download

internal enum class OfflineDownloadResponseMode {
    REPLACE,
    APPEND,
    RETRY_FROM_ZERO,
}

internal fun offlineDownloadResponseMode(
    existingBytes: Long,
    responseCode: Int,
    contentRangeHeader: String?,
): OfflineDownloadResponseMode {
    val normalizedExistingBytes = existingBytes.coerceAtLeast(0L)
    if (responseCode != 206) return OfflineDownloadResponseMode.REPLACE

    val rangeStart = contentRangeStart(contentRangeHeader)
        ?: return OfflineDownloadResponseMode.RETRY_FROM_ZERO

    return when {
        normalizedExistingBytes > 0L && rangeStart == normalizedExistingBytes -> {
            OfflineDownloadResponseMode.APPEND
        }
        normalizedExistingBytes == 0L && rangeStart == 0L -> {
            OfflineDownloadResponseMode.REPLACE
        }
        else -> OfflineDownloadResponseMode.RETRY_FROM_ZERO
    }
}

internal fun shouldRestartOfflineDownloadFromZero(
    existingBytes: Long,
    responseCode: Int,
): Boolean = existingBytes > 0L && responseCode == 416

internal fun offlineDownloadTotalBytes(
    startBytes: Long,
    bodyLength: Long,
): Long? {
    if (startBytes < 0L || bodyLength < 0L) return null
    if (startBytes > Long.MAX_VALUE - bodyLength) return null
    return startBytes + bodyLength
}

private fun contentRangeStart(value: String?): Long? {
    val normalized = value?.trim().orEmpty()
    val match = CONTENT_RANGE_PATTERN.matchEntire(normalized) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    if (end < start) return null

    val totalValue = match.groupValues[3]
    if (totalValue != "*") {
        val total = totalValue.toLongOrNull() ?: return null
        if (total <= end) return null
    }
    return start
}

private val CONTENT_RANGE_PATTERN = Regex(
    pattern = "bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)",
    option = RegexOption.IGNORE_CASE,
)
