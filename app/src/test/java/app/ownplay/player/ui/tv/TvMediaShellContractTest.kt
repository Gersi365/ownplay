package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMediaShellContractTest {
    @Test
    fun `primary tv destinations follow the approved media shell order`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvDestination.kt"),
        )
        val orderedDestinations = listOf(
            "TvDestination.HOME",
            "TvDestination.LIVE_TV",
            "TvDestination.MOVIES",
            "TvDestination.SERIES",
            "TvDestination.SETTINGS",
        )
        val positions = orderedDestinations.map(source::indexOf)

        assertTrue("Every approved destination must be present.", positions.all { it >= 0 })
        assertTrue(
            "Primary TV destinations must remain Home, Live TV, Movies, Series, Settings.",
            positions.zipWithNext().all { (left, right) -> left < right },
        )
        assertTrue("Home label must remain explicit.", "label = \"Home\"" in source)
        assertTrue("Live TV label must remain explicit.", "label = \"Live TV\"" in source)
        assertTrue("Movies label must remain explicit.", "label = \"Movies\"" in source)
        assertTrue("Series label must remain explicit.", "label = \"Series\"" in source)
        assertTrue("Settings label must remain explicit.", "label = \"Settings\"" in source)
    }

    @Test
    fun `home is the deterministic default destination`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvDestination.kt"),
        )

        assertTrue(
            "Home must remain the deterministic shell default.",
            "defaultTvDestination: TvDestination = TvDestination.HOME" in source,
        )
    }

    @Test
    fun `search discover and library are not primary destinations`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvDestination.kt"),
        )

        assertFalse("label = \"Search\"" in source)
        assertFalse("label = \"Discover\"" in source)
        assertFalse("label = \"Library\"" in source)
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
