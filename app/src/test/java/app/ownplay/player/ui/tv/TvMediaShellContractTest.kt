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
            "Primary TV destinations must remain Home, Live, Movies, Series, Settings.",
            positions.zipWithNext().all { (left, right) -> left < right },
        )
        assertTrue("Home label must remain explicit.", "label = \"Home\"" in source)
        assertTrue("Live label must remain explicit.", "label = \"Live\"" in source)
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
    fun `rail starts expanded and right publishes an explicit destination scoped handoff`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )

        assertTrue("Rail must start expanded.", "var railExpanded by remember { mutableStateOf(true) }" in source)
        assertTrue(
            "Right must activate the focused destination before content entry is armed.",
            "onDestinationActivated(destination) pendingContentEntry = destination" in source,
        )
        assertTrue(
            "A matched destination must publish an explicit entry generation.",
            "contentEntryDestination = destination contentEntryGeneration += 1" in source,
        )
        assertTrue(
            "Entry generation must only be visible to the active destination that owns it.",
            "contentEntryGeneration = if (contentEntryDestination == activeDestination)" in source,
        )
        assertFalse(
            "Primary TV content handoff must not fall back to spatial D-pad focus search.",
            "moveFocus(FocusDirection.Right)" in source,
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
        assertTrue(
            "The shared shell callback must only restore the rail while it is visible.",
            "if (railVisible) { railExpanded = true pendingRailRestore = activeDestination }" in source,
        )
    }

    @Test
    fun `old horizontal primary navigation and temporary home bridge are no longer active`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )

        assertFalse("TVPrimaryNavigationBar(" in source)
        assertFalse("TVSection.LIBRARY" in source)
        assertFalse("UnifiedLibraryRoute(" in source)
        assertFalse("homeBridgeFullscreen" in source)
        assertTrue("TvMediaShell(" in source)
        assertTrue("TvDestination.HOME -> TvHomeScreen(" in source)
    }

    @Test
    fun `movies and series use dedicated tv presentations while fullscreen playback is preserved`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )

        assertTrue(
            "Movies catalog, details and fullscreen playback must route through dedicated TV presentation layers.",
            "if (vodFullscreen) { val movie = onDemandPresentation.moviePlayback" in source &&
                "TvMoviePlaybackRoute(" in source &&
                "else { TvMoviesRoute(" in source,
        )
        assertFalse(
            "TV Movie fullscreen must not regress to the shared VOD route.",
            "VodRoute(" in source,
        )
        assertTrue(
            "Series catalog and details must route to the dedicated TV presentation.",
            "if (seriesFullscreen) { SeriesRoute(" in source && "else { TvSeriesRoute(" in source,
        )
        assertTrue(
            "Opening Movies must preserve the existing on-demand movie catalog session.",
            "runtime.onDemandPresentationSession::showMovieCatalog" in source,
        )
        assertTrue(
            "Opening Series must preserve the existing on-demand series catalog session.",
            "runtime.onDemandPresentationSession::showSeriesCatalog" in source,
        )
    }

    @Test
    fun `home originated movie and series details return to home`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )

        assertTrue(
            "Home-originated Movie details must remember that their shell return target is Home.",
            "movieDetailReturnToHome = true openDestination(TvDestination.MOVIES)" in source,
        )
        assertTrue(
            "Home-originated Series details must remember that their shell return target is Home.",
            "seriesDetailReturnToHome = true openDestination(TvDestination.SERIES)" in source,
        )
        assertTrue(
            "Movie detail Back must retain the shared Home return contract.",
            "returnToLibraryOnDetailBack = movieDetailReturnToHome" in source,
        )
        assertTrue(
            "Movie detail Back must arm Home focus restoration only for a Home-originated detail.",
            "if (movieDetailReturnToHome && homeReturnFocusKey != null)" in source,
        )
        assertTrue(
            "Series detail Back must retain the shared Home return contract.",
            "returnToLibraryOnDetailBack = seriesDetailReturnToHome" in source,
        )
        assertTrue(
            "Series detail Back must arm Home focus restoration only for a Home-originated detail.",
            "if (seriesDetailReturnToHome && homeReturnFocusKey != null)" in source,
        )
        assertTrue(
            "Detail return must still resolve to the Home shell destination.",
            "openDestination(TvDestination.HOME)" in source,
        )
    }
}
