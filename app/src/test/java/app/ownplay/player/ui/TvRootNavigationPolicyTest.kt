package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvRootNavigationPolicyTest {
    @Test
    fun primarySectionsReturnToHome() {
        listOf(
            TvOwnPlaySection.LIVE,
            TvOwnPlaySection.LIBRARY,
            TvOwnPlaySection.SETTINGS,
        ).forEach { section ->
            assertEquals(
                TvOwnPlaySection.HOME,
                tvPrimarySectionBackDestination(section),
            )
        }
    }

    @Test
    fun homeAndDetailHierarchyDoNotUsePrimarySectionBackHandling() {
        listOf(
            TvOwnPlaySection.HOME,
            TvOwnPlaySection.MOVIE_DETAILS,
            TvOwnPlaySection.SERIES_DETAILS,
        ).forEach { section ->
            assertNull(tvPrimarySectionBackDestination(section))
        }
    }
}
