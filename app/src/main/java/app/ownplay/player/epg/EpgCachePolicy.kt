package app.ownplay.player.epg

internal fun isEpgCacheFresh(
    loadedAtEpochSeconds: Long,
    nowEpochSeconds: Long,
    ttlSeconds: Long,
): Boolean {
    if (ttlSeconds <= 0L) return false
    if (loadedAtEpochSeconds < 0L || nowEpochSeconds < loadedAtEpochSeconds) return false
    return nowEpochSeconds - loadedAtEpochSeconds < ttlSeconds
}
