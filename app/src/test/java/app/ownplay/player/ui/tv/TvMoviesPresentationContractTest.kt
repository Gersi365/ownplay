package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMoviesPresentationContractTest {
    private val appSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )
    }
    private val moviesSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/movies/TvMoviesRoute.kt"),
        )
    }

    @Test
    fun `tv movies catalog and details use dedicated tv presentation`() {
        assertTrue(
            "TV Movies must route non-fullscreen presentation through the dedicated TV route.",
            "if (vodFullscreen) { VodRoute(" in appSource && "else { TvMoviesRoute(" in appSource,
        )
        assertTrue(
            "The dedicated route must own TV catalog geometry.",
            "private fun TvMoviesCatalogScreen(" in moviesSource,
        )
        assertTrue(
            "The dedicated route must own TV detail geometry.",
            "private fun TvMovieDetailsScreen(" in moviesSource,
        )
    }

    @Test
    fun `existing vod playback remains the fullscreen owner`() {
        assertTrue(
            "Fullscreen Movie playback must continue through the established VOD route.",
            "if (vodFullscreen) { VodRoute(" in appSource,
        )
        assertFalse(
            "The TV catalog route must not own a PlayerView.",
            "PlayerView(" in moviesSource,
        )
        assertFalse(
            "The TV catalog route must not bind playback video output directly.",
            "playbackVideoOutput.bind" in moviesSource,
        )
    }

    @Test
    fun `tv movies exposes remote first library sections without download ui`() {
        assertTrue("Continue Watching must remain first-class.", "\"Continue Watching\"" in moviesSource)
        assertTrue("All Movies must remain directly reachable.", "\"All Movies\"" in moviesSource)
        assertTrue("Favorites must remain directly reachable.", "\"Favorites\"" in moviesSource)
        assertTrue(
            "Provider categories must remain represented in the TV section rail.",
            "MOVIES_CATEGORY_PREFIX + category.providerCategoryKey" in moviesSource,
        )
        assertFalse("TV Movies must not expose Offline download models.", "OfflineDownload" in moviesSource)
        assertFalse("TV Movies must not instantiate download runtime.", "downloadRuntime" in moviesSource)
        assertFalse("TV Movies must not render a Download action.", "\"Download\"" in moviesSource)
    }

    @Test
    fun `movie details preserve resume and play from beginning semantics`() {
        assertTrue(
            "Incomplete progress must expose Resume as the primary action.",
            "label = if (movie.resumeAvailable) \"Resume\" else \"Play\"" in moviesSource,
        )
        assertTrue(
            "Incomplete progress must expose Play from beginning.",
            "label = \"Play from beginning\"" in moviesSource,
        )
        assertTrue(
            "Play from beginning must remove only the playback snapshot resume position.",
            "movie.copy( positionMs = null, progressCompleted = false" in moviesSource,
        )
        assertFalse(
            "Play from beginning must not prematurely delete persisted progress.",
            "clearProgress(" in moviesSource,
        )
    }

    @Test
    fun `movie detail back restores the originating movie focus`() {
        assertTrue(
            "Movie details must register Back with the established interaction bridge.",
            "PlaybackInteractionBridge.registerBackAction(detailsBackOwner, ::closeMovieDetails)" in moviesSource,
        )
        assertTrue(
            "Closing details must remember the originating movie.",
            "val originMovieId = selectedMovie?.movieId" in moviesSource,
        )
        assertTrue(
            "Closing details must arm explicit movie focus restoration.",
            "movieFocusTargetId = originMovieId movieFocusRequestGeneration += 1" in moviesSource,
        )
        assertTrue(
            "Focus restoration must scroll the movie into view before requesting focus.",
            "gridState.scrollToItem(index)" in moviesSource &&
                "movieFocusRequester.requestFocus()" in moviesSource,
        )
    }

    @Test
    fun `movie focus changes use stable geometry`() {
        assertTrue(
            "Movie cards must track focus explicitly for color-based emphasis.",
            ".onFocusChanged { focused = it.isFocused }" in moviesSource,
        )
        assertFalse("Focused Movies must not scale.", ".scale(" in moviesSource)
        assertFalse("Focused Movies must not animate card size.", "animateContentSize" in moviesSource)
    }

    @Test
    fun `empty movie sections do not trap right navigation`() {
        assertTrue(
            "Right from an empty section must remain unconsumed.",
            "val movie = movies.firstOrNull() if (movie == null) { false } else" in moviesSource,
        )
        assertTrue(
            "Category Right handling must return the actual consumption decision.",
            "event.key == Key.DirectionRight) { onRight() } else" in moviesSource,
        )
    }
}
