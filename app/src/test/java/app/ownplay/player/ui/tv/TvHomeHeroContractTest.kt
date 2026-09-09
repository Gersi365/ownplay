package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeHeroContractTest {
    private val source by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/home/TvHomeScreen.kt"),
        )
    }

    @Test
    fun `home hero follows real data fallback order`() {
        assertTrue(
            "Home hero must prefer the existing Continue Watching ordering.",
            "continueWatching.firstOrNull()?.let" in source,
        )
        assertTrue(
            "Home hero must fall back to a deterministic Movie item.",
            "movies.firstOrNull()?.let" in source,
        )
        assertTrue(
            "Home hero must fall back to a deterministic Series item.",
            "series.firstOrNull()?.let" in source,
        )
        assertTrue(
            "Home hero must reuse real provider artwork instead of a mock asset.",
            "posterUrl = item.movie.posterUrl" in source &&
                "posterUrl = item.episode.posterUrl" in source &&
                "posterUrl = movie.posterUrl" in source &&
                "posterUrl = item.posterUrl" in source,
        )
        assertFalse("Home hero must not invent Trending metadata.", "TRENDING" in source)
        assertFalse("Home hero must not introduce a recommendation backend.", "recommendation" in source.lowercase())
    }

    @Test
    fun `home hero remains informational and preserves shelf focus indexing`() {
        val heroStart = source.indexOf("private fun TvHomeHero(")
        val resolverStart = source.indexOf("private fun resolveHomeHero(")
        assertTrue("Home hero composable must exist.", heroStart >= 0)
        assertTrue("Home hero resolver must follow the composable.", resolverStart > heroStart)

        val heroBlock = source.substring(heroStart, resolverStart)
        assertFalse("The informational Home hero must not add an independent click target.", ".clickable(" in heroBlock)
        assertFalse("The informational Home hero must not add focus ownership.", ".focusRequester(" in heroBlock)
        assertTrue(
            "The hero must replace the existing header slot rather than shifting shelf row indices.",
            "item(key = \"home-header\") { TvHomeHero(model = hero) }" in source,
        )
        assertTrue(
            "Shelf focus restoration must still treat the header slot as row zero.",
            "var rowIndex = 1" in source,
        )
    }
}
