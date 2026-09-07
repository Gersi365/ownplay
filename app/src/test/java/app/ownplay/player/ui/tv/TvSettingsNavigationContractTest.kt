package app.ownplay.player.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvSettingsNavigationContractTest {
    @Test
    fun `root destinations follow the tv settings contract order`() {
        assertEquals(
            listOf(
                "Playlists",
                "Live Management",
                "Backup & Restore",
                "About",
            ),
            tvSettingsDestinations.map { destination -> destination.title },
        )
    }

    @Test
    fun `playlists is the deterministic default root destination`() {
        assertEquals(
            TvSettingsDestination.PLAYLISTS,
            defaultTvSettingsDestination,
        )
    }

    @Test
    fun `generic landscape categories are not tv root destinations`() {
        val titles = tvSettingsDestinations.map { destination -> destination.title }

        assertFalse("Interface" in titles)
        assertFalse("Content" in titles)
        assertFalse("Downloads" in titles)
    }
}
