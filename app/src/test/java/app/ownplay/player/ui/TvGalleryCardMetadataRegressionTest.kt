package app.ownplay.player.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvGalleryCardMetadataRegressionTest {
    @Test
    fun liveGalleryCardOmitsCategoryAndMarqueesFocusedLongTitles() {
        val source = source("src/main/java/app/ownplay/player/ui/live/TvLiveRoute.kt")
        val card = source.substringBetween(
            "private fun TvLiveChannelCard(",
            "private fun TvRemoteChannelLogo(",
        )

        assertFalse(card.contains("channel.categoryName"))
        assertTrue(card.contains("Modifier.basicMarquee(iterations = Int.MAX_VALUE)"))
        assertTrue(card.contains("maxLines = 1"))
        assertTrue(card.contains("softWrap = false"))
    }

    @Test
    fun tvLibraryGalleryCardsDoNotAddMovieOrSeriesTypeLabels() {
        val source = source("src/main/java/app/ownplay/player/ui/library/TvLibraryRoute.kt")
        val movieContent = source.substringBetween(
            "private fun TvMovieContent(",
            "private fun TvSeriesContent(",
        )
        val seriesContent = source.substringBetween(
            "private fun TvSeriesContent(",
            "private fun <T> TvMediaCollection(",
        )
        val tile = source.substringBetween(
            "private fun TvMediaTile(",
            "private fun TvMediaRow(",
        )

        assertFalse(movieContent.contains("Text(\"Movie\""))
        assertFalse(seriesContent.contains("Text(\"Series\""))
        assertFalse(tile.contains("category"))
        assertFalse(tile.contains("\"Movie\""))
        assertFalse(tile.contains("\"Series\""))
    }

    private fun source(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Source file not found: $relativeToApp")
        return file.readText()
    }

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing start marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return substring(startIndex, endIndex)
    }
}
