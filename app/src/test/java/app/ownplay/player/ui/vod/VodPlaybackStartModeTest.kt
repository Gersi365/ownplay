package app.ownplay.player.ui.vod

import app.ownplay.player.vod.VodMovie
import org.junit.Assert.assertEquals
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
