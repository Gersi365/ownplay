package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeBaselineContractTest {
    private val source by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/home/TvHomeScreen.kt"),
        )
    }

    @Test
    fun `home uses only real baseline shelves`() {
        assertTrue("Continue Watching must remain a first-class Home shelf.", "title = \"Continue Watching\"" in source)
        assertTrue("Movies must remain a direct Home shelf.", "title = \"Movies\"" in source)
        assertTrue("Series must remain a direct Home shelf.", "title = \"Series\"" in source)

        assertFalse("Home must not invent a Trending shelf.", "Trending" in source)
        assertFalse("Home must not invent a Discover shelf.", "Discover" in source)
        assertFalse("Home must not reintroduce Library as a shelf.", "TvHomeShelf(title = \"Library\")" in source)
        assertFalse("Home must not invent a Search surface.", "Search" in source)
    }

    @Test
    fun `home is cache first and refreshes only supported on demand sources`() {
        assertTrue("Movies must be observed from the existing cached catalog.", "sourceId?.let(vodRuntime::observeCatalog)" in source)
        assertTrue("Series must be observed from the existing cached catalog.", "sourceId?.let(seriesRuntime::observeCatalog)" in source)
        assertTrue(
            "Automatic refresh must be limited to Xtream sources.",
            "sourceKind != SourceKinds.XTREAM" in source,
        )
        assertTrue("Movie refresh must reuse VodFeatureRuntime.", "vodRuntime.refresh(resolvedSourceId)" in source)
        assertTrue("Series refresh must reuse SeriesFeatureRuntime.", "seriesRuntime.refresh(resolvedSourceId)" in source)
        assertFalse("Home must not create a provider client directly.", "XtreamClient(" in source)
        assertFalse("Home must not create a series provider client directly.", "XtreamSeriesClient(" in source)
    }

    @Test
    fun `continue watching combines movies and episodes by real progress recency`() {
        assertTrue(
            "Movie progress rows must feed Home Continue Watching.",
            "vodCatalog.continueWatching.forEach { add(TvHomeContinueItem.Movie(it)) }" in source,
        )
        assertTrue(
            "Series episode progress rows must feed Home Continue Watching.",
            "seriesCatalog.continueWatching.forEach { add(TvHomeContinueItem.Episode(it)) }" in source,
        )
        assertTrue(
            "Combined progress must be ordered by persisted update time.",
            ".sortedByDescending { item -> item.updatedAtEpochMillis ?: 0L }" in source,
        )
        assertTrue(
            "Continue Watching must remain bounded for TV rendering.",
            ".take(HOME_CONTINUE_WATCHING_LIMIT)" in source,
        )
    }

    @Test
    fun `home focus styling preserves card geometry`() {
        assertTrue("Poster width must remain fixed outside focus state.", ".width(HomePosterWidth)" in source)
        assertTrue("Card focus must be observed explicitly.", ".onFocusChanged { focused = it.isFocused }" in source)
        assertTrue(
            "Focus must be expressed through color rather than geometry.",
            "color = if (focused) { MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) } else { MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) }" in source,
        )
        assertFalse("Home cards must not scale on focus.", ".scale(" in source)
        assertFalse("Home cards must not animate their size on focus.", "animateContentSize" in source)
    }

    @Test
    fun `home cards route to details without owning playback`() {
        assertTrue(
            "Movie cards must open the existing Movie detail callback with their stable focus key.",
            "onOpenMovieDetails(sourceId, movie.movieId, focusKey)" in source,
        )
        assertTrue(
            "Series cards must open the existing Series detail callback with their stable focus key.",
            "onOpenSeriesDetails(sourceId, item.seriesId, focusKey)" in source,
        )
        assertTrue(
            "Continue Watching episodes must open their Series detail context with their stable focus key.",
            "onOpenSeriesDetails(sourceId, episode.seriesId, focusKey)" in source,
        )
        assertFalse("Home must not start playback directly.", "playbackController" in source)
        assertFalse("Home must not own playback interaction bridges.", "PlaybackInteractionBridge" in source)
    }

    @Test
    fun `unsupported or missing playlists have explicit remote usable states`() {
        assertTrue("Missing source must offer Settings.", "title = \"No playlist configured\"" in source)
        assertTrue("Unsupported on-demand sources must be explained.", "title = \"No on-demand catalog for this playlist\"" in source)
        assertTrue("Empty supported catalogs must be explicit.", "title = \"No Movies or Series found\"" in source)
        assertTrue(
            "Empty states must retain a remote action.",
            "TextButton(" in source && "onClick = onAction" in source,
        )
        assertTrue(
            "The empty-state action must remain part of the deterministic Home rail boundary.",
            ".focusRequester(entryFocusRequester)" in source,
        )
    }
}
