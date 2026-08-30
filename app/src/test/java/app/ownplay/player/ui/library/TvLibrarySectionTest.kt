package app.ownplay.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TvLibrarySectionTest {
    @Test
    fun sectionOrderStartsOfflineThenMoviesThenSeries() {
        assertEquals(
            listOf(
                TvLibrarySection.OFFLINE,
                TvLibrarySection.MOVIES,
                TvLibrarySection.SERIES,
            ),
            TvLibrarySection.entries,
        )
    }

    @Test
    fun mobileSectionOrderMatchesTvWithoutAnAggregateAllSection() {
        assertEquals(
            listOf(
                UnifiedLibrarySection.OFFLINE,
                UnifiedLibrarySection.MOVIES,
                UnifiedLibrarySection.SERIES,
            ),
            UnifiedLibrarySection.entries,
        )
    }
}
