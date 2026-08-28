package app.ownplay.player.persistence.download

import app.ownplay.player.download.OfflineDownload
import app.ownplay.player.download.OfflineDownloadSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadEntityRedactionTest {
    @Test
    fun persistenceRenderingRedactsOpaqueIdsPosterTokenAndStorageLocation() {
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

    @Test
    fun runtimeModelsRedactOpaqueIdsAndPosterTokens() {
        val spec = OfflineDownloadSpec(
            sourceId = "source-spec-secret",
            mediaKind = DownloadMediaKinds.MOVIE,
            contentId = "content-spec-secret",
            providerStreamId = 7,
            title = "Fixture",
            posterUrl = "https://img.example/poster?token=spec-poster-secret",
        )
        val download = OfflineDownload(
            downloadId = "download-runtime-secret",
            sourceId = "source-runtime-secret",
            mediaKind = DownloadMediaKinds.MOVIE,
            contentId = "content-runtime-secret",
            title = "Fixture",
            seriesTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            posterUrl = "https://img.example/poster?token=runtime-poster-secret",
            state = DownloadStates.COMPLETED,
            bytesDownloaded = 2048L,
            totalBytes = 2048L,
            failureReason = null,
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
            savedToDownloads = true,
        )

        val rendered = spec.toString() + download.toString()
        listOf(
            "source-spec-secret",
            "content-spec-secret",
            "spec-poster-secret",
            "download-runtime-secret",
            "source-runtime-secret",
            "content-runtime-secret",
            "runtime-poster-secret",
        ).forEach { secret ->
            assertFalse(rendered.contains(secret))
        }
        assertTrue(rendered.contains("<opaque>"))
        assertTrue(rendered.contains("<redacted>"))
    }
}
