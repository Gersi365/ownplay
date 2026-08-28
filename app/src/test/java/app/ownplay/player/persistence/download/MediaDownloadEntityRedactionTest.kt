package app.ownplay.player.persistence.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadEntityRedactionTest {
    @Test
    fun renderingRedactsOpaqueIdsPosterTokenAndStorageLocation() {
        val rendered = MediaDownloadEntity(
            downloadId = "download-secret-id",
            sourceId = "source-secret-id",
            mediaKind = DownloadMediaKinds.MOVIE,
            contentId = "content-secret-id",
            providerStreamId = 42,
            title = "Fixture Movie",
            seriesTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            posterUrl = "https://img.example/poster.jpg?token=poster-secret-token",
            containerExtension = "mp4",
            state = DownloadStates.COMPLETED,
            bytesDownloaded = 1024L,
            totalBytes = 1024L,
            localRelativePath = "content://media/external/downloads/secret-location-id",
            failureReason = null,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        ).toString()

        listOf(
            "download-secret-id",
            "source-secret-id",
            "content-secret-id",
            "poster-secret-token",
            "secret-location-id",
        ).forEach { secret ->
            assertFalse(rendered.contains(secret))
        }
        assertTrue(rendered.contains("<opaque>"))
        assertTrue(rendered.contains("<redacted>"))
        assertTrue(rendered.contains("Fixture Movie"))
        assertTrue(rendered.contains("COMPLETED"))
    }
}
