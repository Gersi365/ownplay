package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeFocusRestorationContractTest {
    private val homeSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/home/TvHomeScreen.kt"),
        )
    }
    private val appSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )
    }

    @Test
    fun `home cards use stable focus namespaces across shelves`() {
        assertTrue("Continue Watching movies need their own stable focus namespace.", "HOME_CONTINUE_MOVIE_PREFIX = \"continue-movie:\"" in homeSource)
        assertTrue("Continue Watching episodes need their own stable focus namespace.", "HOME_CONTINUE_EPISODE_PREFIX = \"continue-episode:\"" in homeSource)
        assertTrue("Movies need their own stable focus namespace.", "HOME_MOVIE_PREFIX = \"movie:\"" in homeSource)
        assertTrue("Series need their own stable focus namespace.", "HOME_SERIES_PREFIX = \"series:\"" in homeSource)
    }

    @Test
    fun `home return restores vertical shelf and horizontal card position before focus`() {
        assertTrue("Home must restore the originating vertical shelf.", "homeListState.scrollToItem(location.rowIndex)" in homeSource)
        assertTrue("Continue Watching must restore horizontal position.", "continueWatchingState.scrollToItem(location.itemIndex)" in homeSource)
        assertTrue("Movies must restore horizontal position.", "moviesState.scrollToItem(location.itemIndex)" in homeSource)
        assertTrue("Series must restore horizontal position.", "seriesState.scrollToItem(location.itemIndex)" in homeSource)
        assertTrue("Focus restore must wait for composition before requesting focus.", "withFrameNanos { }" in homeSource)
        assertTrue("The resolved originating card must receive focus explicitly.", "returnFocusRequester.requestFocus()" in homeSource)
        assertTrue("A completed restore must be consumed once.", "onReturnFocusConsumed()" in homeSource)
    }

    @Test
    fun `only the requested stable card owns the restore requester`() {
        assertTrue(
            "The focus requester must only attach to the exact stable return key.",
            "if (focusKey == returnFocusKey) { Modifier.focusRequester(returnFocusRequester) }" in homeSource,
        )
        assertFalse("Focus restoration must not resize cards.", ".scale(" in homeSource)
        assertFalse("Focus restoration must not animate card geometry.", "animateContentSize" in homeSource)
    }

    @Test
    fun `shell captures origin before details and arms restore only on detail return`() {
        assertTrue("Home must capture the origin key before leaving for details.", "homeReturnFocusKey = focusKey homeReturnFocusPending = false" in appSource)
        assertTrue(
            "Movie detail return must arm restoration explicitly.",
            "if (movieDetailReturnToHome && homeReturnFocusKey != null) { homeReturnFocusGeneration += 1 homeReturnFocusPending = true }" in appSource,
        )
        assertTrue(
            "Series detail return must arm restoration explicitly.",
            "if (seriesDetailReturnToHome && homeReturnFocusKey != null) { homeReturnFocusGeneration += 1 homeReturnFocusPending = true }" in appSource,
        )
        assertTrue(
            "Home must only receive the stored key while a direct detail return is pending.",
            "returnFocusKey = homeReturnFocusKey.takeIf { homeReturnFocusPending }" in appSource,
        )
    }

    @Test
    fun `consumed and generic home navigation clear stale restore state`() {
        assertTrue(
            "Consuming Home restore must clear pending state and the old key.",
            "onReturnFocusConsumed = { homeReturnFocusPending = false homeReturnFocusKey = null }" in appSource,
        )
        assertTrue(
            "Generic navigation to Home must not replay an old card restore.",
            "if (target == TvDestination.HOME && !homeReturnFocusPending) { homeReturnFocusKey = null }" in appSource,
        )
    }
}
