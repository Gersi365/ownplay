package app.ownplay.player.ui.library

internal fun shouldUseOfflineMoviePrimaryAction(
    offlineOnly: Boolean,
    verifiedOffline: Boolean,
): Boolean = offlineOnly && verifiedOffline

internal fun shouldUseOfflineSeriesPrimaryAction(
    offlineOnly: Boolean,
    offlineEpisodeCount: Int,
): Boolean = offlineOnly && offlineEpisodeCount > 0
