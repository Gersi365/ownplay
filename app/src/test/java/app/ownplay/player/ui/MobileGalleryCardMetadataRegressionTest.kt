package app.ownplay.player.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileGalleryCardMetadataRegressionTest {
    @Test
    fun `Mobile Live Gallery omits category and keeps title on one marquee line`() {
        val source = source("src/main/java/app/ownplay/player/ui/live/PortraitLiveViewModes.kt")
        val card = source.substringBetween(
            "private fun LiveChannelCard(",
            "private fun LiveChannelTrailingState(",
        )

        assertFalse(card.contains("channel.categoryName"))
        assertTrue(card.contains("Modifier.basicMarquee(iterations = Int.MAX_VALUE)"))
        assertTrue(card.contains("maxLines = 1"))
        assertTrue(card.contains("softWrap = false"))
        assertTrue(card.contains("currentProgram?.let"))
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
