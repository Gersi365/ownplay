package app.ownplay.player.ui

import app.ownplay.player.testing.sourceBlockAfter
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMoviePlaybackPresentationIsolationContractTest {
    @Test
    fun `tv shell routes movie fullscreen to dedicated tv adapter`() {
        val shell = sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt")
        val moviesBlock = sourceBlockAfter(shell, "TvDestination.MOVIES -> {")

        assertTrue(shell.contains("import app.ownplay.player.ui.movies.TvMoviePlaybackRoute"))
        assertTrue(moviesBlock.contains("TvMoviePlaybackRoute("))
        assertFalse(moviesBlock.contains("VodRoute("))
    }

    @Test
    fun `movie adapter reuses tv on-demand presentation without touch chrome`() {
        val route = sourceText("src/tv/java/app/ownplay/player/ui/movies/TvMoviePlaybackRoute.kt")

        assertTrue(route.contains("OnDemandPlaybackSurface("))
        assertTrue(route.contains("PlaybackInteractionBridge.registerBackAction"))
        assertTrue(route.contains("featureRuntime.saveProgress"))
        assertTrue(route.contains("stopIfCurrent"))
        assertFalse(route.contains("Slider("))
        assertFalse(route.contains("IconButton("))
        assertFalse(route.contains("pointerInput"))
        assertFalse(route.contains("detectTapGestures"))
    }
}
