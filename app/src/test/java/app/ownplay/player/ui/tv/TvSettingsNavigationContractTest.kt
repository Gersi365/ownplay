package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `returning from a tv settings subpage restores the originating root row`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )

        assertTrue(
            "Opening a destination must remember the row that originated the subpage.",
            "originatingDestination = destination" in source &&
                "openDestination = destination" in source,
        )
        assertTrue(
            "Returning to root must restore the originating destination as the focused model state.",
            "if (openDestination == null) { focusedDestination = originatingDestination" in source,
        )
        assertTrue(
            "Returning to root must explicitly request focus on the originating destination row.",
            "rootFocusRequesters.getValue(originatingDestination).requestFocus()" in source,
        )
    }
}
