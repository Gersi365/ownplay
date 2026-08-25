package app.ownplay.player.download

internal const val OFFLINE_DOWNLOAD_FREE_SPACE_RESERVE_BYTES = 256L * 1024L * 1024L

internal fun hasEnoughOfflineDownloadSpace(
    usableSpaceBytes: Long,
    requiredBytes: Long,
): Boolean {
    val usable = usableSpaceBytes.coerceAtLeast(0L)
    val required = requiredBytes.coerceAtLeast(0L)
    if (usable <= OFFLINE_DOWNLOAD_FREE_SPACE_RESERVE_BYTES) return false
    return required <= usable - OFFLINE_DOWNLOAD_FREE_SPACE_RESERVE_BYTES
}
