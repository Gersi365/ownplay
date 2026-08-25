package app.ownplay.player.ui.library

import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.persistence.download.DownloadMediaKinds

internal data class LibrarySeriesKey(
    val sourceId: String,
    val identity: String,
)

internal data class LibrarySeriesGroup(
    val key: LibrarySeriesKey,
    val title: String,
    val posterUrl: String?,
    val episodes: List<OfflineDownload>,
) {
    val episodeCount: Int
        get() = episodes.size

    val seasonNumbers: List<Int>
        get() = episodes.mapNotNull { it.seasonNumber }.distinct().sorted()

    val seasonCount: Int
        get() = seasonNumbers.size

    val totalBytesDownloaded: Long
        get() = episodes.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }

    val latestUpdatedAtEpochMillis: Long
        get() = episodes.maxOfOrNull { it.updatedAtEpochMillis } ?: 0L
}

internal fun groupLibrarySeries(downloads: List<OfflineDownload>): List<LibrarySeriesGroup> {
    return downloads
        .asSequence()
        .filter { it.mediaKind == DownloadMediaKinds.SERIES_EPISODE }
        .groupBy(::seriesKey)
        .map { (key, groupedEpisodes) ->
            val episodes = groupedEpisodes.sortedWith(
                compareBy<OfflineDownload>(
                    { it.seasonNumber ?: Int.MAX_VALUE },
                    { it.episodeNumber ?: Int.MAX_VALUE },
                    { it.title.lowercase() },
                ),
            )
            val newestFirst = groupedEpisodes.sortedByDescending { it.updatedAtEpochMillis }
            LibrarySeriesGroup(
                key = key,
                title = groupedEpisodes
                    .asSequence()
                    .mapNotNull { it.seriesTitle?.trim()?.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    ?: groupedEpisodes.first().title,
                posterUrl = newestFirst
                    .asSequence()
                    .mapNotNull { it.posterUrl?.takeIf(String::isNotBlank) }
                    .firstOrNull(),
                episodes = episodes,
            )
        }
        .sortedWith(
            compareByDescending<LibrarySeriesGroup> { it.latestUpdatedAtEpochMillis }
                .thenBy { it.title.lowercase() },
        )
}

private fun seriesKey(download: OfflineDownload): LibrarySeriesKey {
    val seriesTitle = download.seriesTitle?.trim()?.takeIf(String::isNotBlank)
    return LibrarySeriesKey(
        sourceId = download.sourceId,
        identity = seriesTitle?.lowercase() ?: "episode:${download.contentId}",
    )
}
