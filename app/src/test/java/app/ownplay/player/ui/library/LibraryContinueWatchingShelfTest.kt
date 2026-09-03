package app.ownplay.player.ui.library

import app.ownplay.player.vod.VodMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryContinueWatchingShelfTest {
    @Test
    fun resumePercentUsesKnownPlaybackBounds() {
        assertEquals(25, libraryResumePercent(positionMs = 15_000L, durationMs = 60_000L))
        assertEquals(50, libraryResumePercent(positionMs = 30_000L, durationMs = 60_000L))
        assertEquals(99, libraryResumePercent(positionMs = 60_000L, durationMs = 60_000L))
    }

    @Test
    fun resumePercentRequiresUsablePositionAndDuration() {
        assertNull(libraryResumePercent(positionMs = null, durationMs = 60_000L))
        assertNull(libraryResumePercent(positionMs = 10_000L, durationMs = null))
        assertNull(libraryResumePercent(positionMs = 0L, durationMs = 60_000L))
        assertNull(libraryResumePercent(positionMs = 10_000L, durationMs = 0L))
    }

    @Test
    fun resumeLabelFallsBackWhenDurationIsUnknown() {
        assertEquals("Resume · 50%", libraryResumeLabel(movie(positionMs = 30_000L, durationMs = 60_000L)))
        assertEquals("Resume", libraryResumeLabel(movie(positionMs = 30_000L, durationMs = null)))
    }

    private fun movie(positionMs: Long?, durationMs: Long?) = VodMovie(
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
        durationMs = durationMs,
        progressCompleted = false,
        progressUpdatedAtEpochMillis = null,
    )
}
