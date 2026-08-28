package app.ownplay.player.ui.library

import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.persistence.download.DownloadMediaKinds
import app.ownplay.player.persistence.download.DownloadStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySeriesGroupingTest {
    @Test
    fun groupsEpisodesBySourceAndSeriesTitleAndSortsThemBySeasonEpisode() {
        val downloads = listOf(
            episode(
                downloadId = "b",
                sourceId = "source-1",
                contentId = "ep-2",
                title = "Second",
                seriesTitle = "North Shore",
                season = 2,
                episode = 1,
                updatedAt = 200L,
            ),
            episode(
                downloadId = "a",
                sourceId = "source-1",
                contentId = "ep-1",
                title = "Pilot",
                seriesTitle = "North Shore",
                season = 1,
                episode = 3,
                updatedAt = 100L,
            ),
        )

        val groups = groupLibrarySeries(downloads)

        assertEquals(1, groups.size)
        assertEquals("North Shore", groups.single().title)
        assertEquals(listOf(1, 2), groups.single().seasonNumbers)
        assertEquals(listOf("ep-1", "ep-2"), groups.single().episodes.map { it.contentId })
    }

    @Test
    fun sameSeriesTitleFromDifferentSourcesDoesNotMerge() {
        val groups = groupLibrarySeries(
            listOf(
                episode("a", "source-1", "ep-1", "One", "Atlas", 1, 1, 100L),
                episode("b", "source-2", "ep-2", "Two", "Atlas", 1, 2, 200L),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(setOf("source-1", "source-2"), groups.map { it.key.sourceId }.toSet())
    }

    @Test
    fun exactSeriesIdsPreventSameTitleSeriesFromMerging() {
        val groups = groupLibrarySeries(
            listOf(
                episode(
                    "a",
                    "source-1",
                    "source-1:series:10:episode:101",
                    "One",
                    "Atlas",
                    1,
                    1,
                    100L,
                ),
                episode(
                    "b",
                    "source-1",
                    "source-1:series:20:episode:201",
                    "Two",
                    "Atlas",
                    1,
                    1,
                    200L,
                ),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(
            setOf("source-1:series:10", "source-1:series:20"),
            groups.mapNotNull { it.seriesId }.toSet(),
        )
    }

    @Test
    fun newestEpisodeSuppliesCurrentSeriesTitleAndPoster() {
        val groups = groupLibrarySeries(
            listOf(
                episode(
                    "older",
                    "source-1",
                    "source-1:series:42:episode:101",
                    "Pilot",
                    "Old title",
                    1,
                    1,
                    100L,
                ),
                episode(
                    "newer",
                    "source-1",
                    "source-1:series:42:episode:102",
                    "Second",
                    "Current title",
                    1,
                    2,
                    200L,
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("Current title", groups.single().title)
        assertEquals("poster-newer", groups.single().posterUrl)
    }

    @Test
    fun extractsSeriesIdFromStableEpisodeContentId() {
        assertEquals(
            "source-1:series:42",
            seriesIdFromEpisodeContentId("source-1:series:42:episode:777"),
        )
        assertNull(seriesIdFromEpisodeContentId("episode-777"))
        assertNull(seriesIdFromEpisodeContentId("source-1:series:42:episode:"))
    }

    @Test
    fun missingSeriesTitleDoesNotMergeUnrelatedEpisodes() {
        val groups = groupLibrarySeries(
            listOf(
                episode("a", "source-1", "ep-1", "Unknown one", null, null, null, 100L),
                episode("b", "source-1", "ep-2", "Unknown two", null, null, null, 200L),
            ),
        )

        assertEquals(2, groups.size)
    }

    private fun episode(
        downloadId: String,
        sourceId: String,
        contentId: String,
        title: String,
        seriesTitle: String?,
        season: Int?,
        episode: Int?,
        updatedAt: Long,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        sourceId = sourceId,
        mediaKind = DownloadMediaKinds.SERIES_EPISODE,
        contentId = contentId,
        title = title,
        seriesTitle = seriesTitle,
        seasonNumber = season,
        episodeNumber = episode,
        posterUrl = "poster-$downloadId",
        state = DownloadStates.COMPLETED,
        bytesDownloaded = 100L,
        totalBytes = 100L,
        failureReason = null,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = updatedAt,
        savedToDownloads = true,
    )
}
