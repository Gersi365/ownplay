package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeRailBoundaryContractTest {
    private val shellSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/shell/TvMediaShell.kt"),
        )
    }
    private val homeSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/home/TvHomeScreen.kt"),
        )
    }

    @Test
    fun `home right handoff uses an explicit entry event instead of spatial focus`() {
        assertTrue(
            "Home must receive an explicit content-entry generation from the shell.",
            "if (destination == TvDestination.HOME) { homeContentEntryGeneration += 1 } else { focusManager.moveFocus(FocusDirection.Right) }" in shellSource,
        )
        assertTrue(
            "Leaving Home must clear the old entry generation so detail returns do not replay rail entry.",
            "if (activeDestination != TvDestination.HOME) { homeContentEntryGeneration = 0 }" in shellSource,
        )
        assertTrue(
            "The Home boundary must be provided only around shell content.",
            "LocalTvHomeShellFocusBoundary provides TvHomeShellFocusBoundary" in shellSource,
        )
    }

    @Test
    fun `home entry priority is continue watching then movies then series then empty action`() {
        val continuePosition = homeSource.indexOf("continueWatching.isNotEmpty() -> continueWatching.first().key")
        val moviePosition = homeSource.indexOf("movies.isNotEmpty() -> homeMovieFocusKey(movies.first().movieId)")
        val seriesPosition = homeSource.indexOf("series.isNotEmpty() -> homeSeriesFocusKey(series.first().seriesId)")
        val emptyPosition = homeSource.lastIndexOf("else -> HOME_EMPTY_ACTION_FOCUS_KEY")

        assertTrue("Continue Watching must be the first real Home entry target.", continuePosition >= 0)
        assertTrue("Movies must be the second Home entry fallback.", moviePosition > continuePosition)
        assertTrue("Series must be the third Home entry fallback.", seriesPosition > moviePosition)
        assertTrue("The remote-usable empty action must be the final fallback.", emptyPosition > seriesPosition)
        assertTrue(
            "Loading with no cached content must wait rather than focus an invisible target.",
            "refreshing && continueWatching.isEmpty() && movies.isEmpty() && series.isEmpty() -> null" in homeSource,
        )
    }

    @Test
    fun `explicit Home entry scrolls the target into composition before focus`() {
        assertTrue(
            "Home must observe the shell entry generation.",
            "shellFocusBoundary.contentEntryGeneration" in homeSource,
        )
        assertTrue(
            "Home must scroll to the resolved vertical shelf before entry focus.",
            "homeListState.scrollToItem(location.rowIndex)" in homeSource,
        )
        assertTrue(
            "Continue Watching entry must restore horizontal item zero explicitly.",
            "TvHomeShelfKind.CONTINUE_WATCHING -> continueWatchingState.scrollToItem(location.itemIndex)" in homeSource,
        )
        assertTrue(
            "Movie entry must restore horizontal item zero explicitly.",
            "TvHomeShelfKind.MOVIES -> moviesState.scrollToItem(location.itemIndex)" in homeSource,
        )
        assertTrue(
            "Series entry must restore horizontal item zero explicitly.",
            "TvHomeShelfKind.SERIES -> seriesState.scrollToItem(location.itemIndex)" in homeSource,
        )
        assertTrue(
            "Home must wait for composition before requesting explicit entry focus.",
            "contentEntryFocusRequester.requestFocus()" in homeSource,
        )
    }

    @Test
    fun `left from every first Home shelf column returns directly to the active rail item`() {
        assertTrue(
            "The shell callback must restore the active destination, not a nearest rail row.",
            "pendingRailRestore = activeDestination" in shellSource,
        )
        assertTrue(
            "Shelf rendering must know the actual first column rather than infer it spatially.",
            "itemsIndexed(" in homeSource && "isLeftBoundary = index == 0" in homeSource,
        )
        assertTrue(
            "Left on a first-column card must call the shell boundary directly.",
            "event.key == Key.DirectionLeft) { onLeftBoundary() true" in homeSource,
        )
        assertTrue(
            "The explicit content-entry requester must remain separate from the left-column rule.",
            "isContentEntry = item.key == contentEntryKey" in homeSource &&
                "isContentEntry = focusKey == contentEntryKey" in homeSource,
        )
        assertTrue(
            "Empty-state actions must participate in the same deterministic rail boundary.",
            ".focusRequester(entryFocusRequester)" in homeSource &&
                "event.key == Key.DirectionLeft) { onEntryLeft() true" in homeSource,
        )
    }

    @Test
    fun `Home rail boundary does not change card geometry`() {
        assertFalse("Boundary focus must not scale Home cards.", ".scale(" in homeSource)
        assertFalse("Boundary focus must not animate Home card size.", "animateContentSize" in homeSource)
        assertTrue("Poster width remains fixed.", ".width(HomePosterWidth)" in homeSource)
    }
}
