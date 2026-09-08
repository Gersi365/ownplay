package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import app.ownplay.player.ui.shell.TvDestination
import app.ownplay.player.ui.shell.defaultTvDestination
import app.ownplay.player.ui.shell.tvDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMediaShellContractTest {
    @Test
    fun `primary tv destinations follow the approved media shell order`() {
        assertEquals(
            listOf(
                "Home",
                "Live TV",
                "Movies",
                "Series",
                "Settings",
            ),
            tvDestinations.map { destination -> destination.label },
        )
    }

    @Test
    fun `home is the deterministic default destination`() {
        assertEquals(TvDestination.HOME, defaultTvDestination)
    }

    @Test
    fun `search discover and library are not primary destinations`() {
        val labels = tvDestinations.map { destination -> destination.label }

        assertFalse("Search" in labels)
        assertFalse("Discover" in labels)
        assertFalse("Library" in labels)
    }

    @Test
    fun `rail starts expanded and right activates destination before content handoff`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )

        assertTrue("Rail must start expanded.", "var railExpanded by remember { mutableStateOf(true) }" in source)
        assertTrue(
            "Right must activate the focused destination before content focus moves.",
            "onDestinationActivated(destination) pendingContentEntry = destination" in source,
        )
        assertTrue(
            "Content handoff must use D-pad Right focus movement.",
            "focusManager.moveFocus(FocusDirection.Right)" in source,
        )
    }

    @Test
    fun `rail collapse and restore are owned by focus transitions`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )

        assertTrue(
            "Rail collapses only after rail focus has left.",
            "if (focusedRailDestination == null) { railExpanded = false }" in source,
        )
        assertTrue(
            "Returning from content expands the rail.",
            "val enteringCollapsedRail = !railExpanded focusedRailDestination = destination railExpanded = true" in source,
        )
        assertTrue(
            "Entry from content must restore the active destination instead of an arbitrary row.",
            "if (enteringCollapsedRail && destination != activeDestination) { pendingRailRestore = activeDestination }" in source,
        )
    }

    @Test
    fun `old horizontal primary navigation is no longer active`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )

        assertFalse("TVPrimaryNavigationBar(" in source)
        assertFalse("TVSection.LIBRARY" in source)
        assertTrue("TvMediaShell(" in source)
        assertTrue("TvDestination.HOME -> UnifiedLibraryRoute(" in source)
    }
}
