package app.ownplay.player.ui.series

import app.ownplay.player.series.SeriesEpisode
import app.ownplay.player.series.SeriesEpisodeProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesPlaybackStartPolicyTest {
    @Test
    fun resumeUsesSavedIncompletePosition() {
        val episode = episode(positionMs = 42_000L, durationMs = 120_000L, completed = false)

        assertEquals(
            42_000L,
            seriesPlaybackStartPosition(episode, SeriesPlaybackStartMode.RESUME),
        )
    }

    @Test
    fun resumeFallsBackToBeginningWhenProgressIsCompleted() {
        assertEquals(
            0L,
            seriesPlaybackStartPosition(
                episode(positionMs = 42_000L, durationMs = 120_000L, completed = true),
                SeriesPlaybackStartMode.RESUME,
            ),
        )
    }

    @Test
    fun fromBeginningAlwaysStartsAtZero() {
        assertEquals(
            0L,
            seriesPlaybackStartPosition(
                episode(positionMs = 42_000L, durationMs = 120_000L, completed = false),
                SeriesPlaybackStartMode.FROM_BEGINNING,
            ),
        )
    }

    @Test
    fun resumePercentUsesKnownPlaybackBounds() {
        assertEquals(25, seriesEpisodeResumePercent(positionMs = 30_000L, durationMs = 120_000L))
        assertEquals(50, seriesEpisodeResumePercent(positionMs = 60_000L, durationMs = 120_000L))
        assertEquals(99, seriesEpisodeResumePercent(positionMs = 120_000L, durationMs = 120_000L))
    }

    @Test
    fun resumePercentRequiresUsableBounds() {
        assertNull(seriesEpisodeResumePercent(positionMs = null, durationMs = 120_000L))
        assertNull(seriesEpisodeResumePercent(positionMs = 30_000L, durationMs = null))
        assertNull(seriesEpisodeResumePercent(positionMs = 0L, durationMs = 120_000L))
        assertNull(seriesEpisodeResumePercent(positionMs = 30_000L, durationMs = 0L))
    }

    @Test
    fun labelsReflectResumeCompletionAndOfflineState() {
        val resumable = episode(positionMs = 60_000L, durationMs = 120_000L, completed = false)
        val watched = episode(positionMs = 120_000L, durationMs = 120_000L, completed = true)
        val fresh = episode(positionMs = null, durationMs = 120_000L, completed = false)

        assertEquals("Resume · 50%", seriesEpisodeResumeLabel(resumable))
        assertEquals("Resume · 50%", seriesEpisodeProgressLabel(resumable))
        assertEquals("Watched", seriesEpisodeProgressLabel(watched))
        assertNull(seriesEpisodeProgressLabel(fresh))
        assertEquals("Resume", seriesEpisodePrimaryPlaybackLabel(resumable, offlineCopyAvailable = false))
        assertEquals("Resume Offline", seriesEpisodePrimaryPlaybackLabel(resumable, offlineCopyAvailable = true))
        assertEquals("Play", seriesEpisodePrimaryPlaybackLabel(fresh, offlineCopyAvailable = false))
        assertEquals("Play Offline", seriesEpisodePrimaryPlaybackLabel(fresh, offlineCopyAvailable = true))
    }

    @Test
    fun savedProgressSnapshotRefreshesEpisodePresentation() {
        val fresh = episode(positionMs = null, durationMs = null, completed = false)
        val progress = SeriesEpisodeProgressSnapshot(
            positionMs = 60_000L,
            durationMs = 120_000L,
            completed = false,
            updatedAtEpochMillis = 99L,
        )

        val synced = seriesEpisodeWithProgress(fresh, progress)

        assertEquals(60_000L, synced.positionMs)
        assertEquals(120_000L, synced.durationMs)
        assertEquals(99L, synced.progressUpdatedAtEpochMillis)
        assertTrue(synced.resumeAvailable)
        assertEquals("Resume · 50%", seriesEpisodeProgressLabel(synced))
    }

    @Test
    fun nullProgressSnapshotClearsEpisodePresentation() {
        val watched = episode(positionMs = 120_000L, durationMs = 120_000L, completed = true)

        val cleared = seriesEpisodeWithProgress(watched, progress = null)

        assertNull(cleared.positionMs)
        assertNull(cleared.durationMs)
        assertFalse(cleared.progressCompleted)
        assertNull(cleared.progressUpdatedAtEpochMillis)
        assertFalse(cleared.resumeAvailable)
        assertNull(seriesEpisodeProgressLabel(cleared))
    }

    private fun episode(
        positionMs: Long?,
        durationMs: Long?,
        completed: Boolean,
    ) = SeriesEpisode(
        episodeId = "episode",
        seriesId = "series",
        seriesTitle = "Series",
        providerEpisodeId = 1,
        seasonNumber = 1,
        episodeNumber = 2,
        title = "Episode",
        containerExtension = null,
        durationSeconds = 120L,
        posterUrl = null,
        positionMs = positionMs,
        durationMs = durationMs,
        progressCompleted = completed,
        progressUpdatedAtEpochMillis = null,
    )
}
