package app.ownplay.player.ui.vod

import app.ownplay.player.vod.VodMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPlaybackStartModeTest {
    @Test
    fun resumeUsesSavedIncompletePositionAboveThreshold() {
        val movie = movie(positionMs = 42_000L, completed = false)

        assertEquals(
            42_000L,
            vodPlaybackStartPosition(movie, VodPlaybackStartMode.RESUME),
        )
    }

    @Test
    fun resumeFallsBackToBeginningForTinyOrCompletedProgress() {
        assertEquals(
            0L,
            vodPlaybackStartPosition(
                movie(positionMs = 5_000L, completed = false),
                VodPlaybackStartMode.RESUME,
            ),
        )
        assertEquals(
            0L,
            vodPlaybackStartPosition(
                movie(positionMs = 42_000L, completed = true),
                VodPlaybackStartMode.RESUME,
            ),
        )
    }

    @Test
    fun fromBeginningAlwaysStartsAtZero() {
        assertEquals(
            0L,
            vodPlaybackStartPosition(
                movie(positionMs = 42_000L, completed = false),
                VodPlaybackStartMode.FROM_BEGINNING,
            ),
        )
    }

    @Test
    fun primaryPlaybackLabelReflectsResumeAndOfflineState() {
        val resumable = movie(positionMs = 42_000L, completed = false)
        val fresh = movie(positionMs = null, completed = false)

        assertEquals("Resume", vodPrimaryPlaybackLabel(resumable, offlineCopyAvailable = false))
        assertEquals("Resume Offline", vodPrimaryPlaybackLabel(resumable, offlineCopyAvailable = true))
        assertEquals("Play", vodPrimaryPlaybackLabel(fresh, offlineCopyAvailable = false))
        assertEquals("Play Offline", vodPrimaryPlaybackLabel(fresh, offlineCopyAvailable = true))
    }

    @Test
    fun catalogProgressReplacesStaleSelectedProgressWithoutChangingFavorite() {
        val selected = movie(positionMs = 12_000L, completed = false).copy(isFavorite = true)
        val latest = movie(positionMs = 72_000L, completed = false).copy(
            isFavorite = false,
            durationMs = 180_000L,
            progressUpdatedAtEpochMillis = 99L,
        )

        val synced = vodMovieWithCatalogProgress(selected, latest)

        assertEquals(72_000L, synced.positionMs)
        assertEquals(180_000L, synced.durationMs)
        assertEquals(99L, synced.progressUpdatedAtEpochMillis)
        assertTrue(synced.isFavorite)
        assertTrue(synced.resumeAvailable)
    }

    @Test
    fun clearedCatalogProgressRemovesResumeState() {
        val selected = movie(positionMs = 72_000L, completed = false)
        val cleared = movie(positionMs = null, completed = false).copy(durationMs = null)

        val synced = vodMovieWithCatalogProgress(selected, cleared)

        assertNull(synced.positionMs)
        assertNull(synced.durationMs)
        assertFalse(synced.progressCompleted)
        assertFalse(synced.resumeAvailable)
    }

    private fun movie(positionMs: Long?, completed: Boolean) = VodMovie(
        movieId = "movie",
        providerStreamId = 1,
        categoryKey = null,
        name = "Movie",
        posterUrl = null,
        containerExtension = null,
        rating = null,
        addedAtEpochSeconds = null,
        isFavorite = false,
        positionMs = positionMs,
        durationMs = 120_000L,
        progressCompleted = completed,
        progressUpdatedAtEpochMillis = null,
    )
}
