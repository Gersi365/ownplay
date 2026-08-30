package app.ownplay.player.ui.live

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveHierarchyPolicyTest {
    @Test
    fun backClosesPreviewBeforeLeavingChannels() {
        assertEquals(
            TvLiveBackAction.CLOSE_PREVIEW,
            tvLiveBackAction(
                level = TvLiveBrowseLevel.CHANNELS,
                hasPreview = true,
            ),
        )
        assertEquals(
            TvLiveBackAction.SHOW_CATEGORIES,
            tvLiveBackAction(
                level = TvLiveBrowseLevel.CHANNELS,
                hasPreview = false,
            ),
        )
        assertEquals(
            TvLiveBackAction.EXIT_LIVE,
            tvLiveBackAction(
                level = TvLiveBrowseLevel.CATEGORIES,
                hasPreview = false,
            ),
        )
    }

    @Test
    fun firstOkOpensPreviewAndSecondOkOnSameChannelOpensFullscreen() {
        assertEquals(
            TvLiveChannelActivation.OPEN_PREVIEW,
            tvLiveChannelActivation(
                channelId = "channel-2",
                previewChannelId = null,
            ),
        )
        assertEquals(
            TvLiveChannelActivation.OPEN_PREVIEW,
            tvLiveChannelActivation(
                channelId = "channel-2",
                previewChannelId = "channel-1",
            ),
        )
        assertEquals(
            TvLiveChannelActivation.OPEN_FULLSCREEN,
            tvLiveChannelActivation(
                channelId = "channel-2",
                previewChannelId = "channel-2",
            ),
        )
    }

    @Test
    fun categoriesAndChannelsRemainSeparatePresentationLevels() {
        val source = appSource(
            "src/main/java/app/ownplay/player/ui/live/TvLiveRoute.kt",
        )

        assertTrue(source.contains("mutableStateOf(TvLiveBrowseLevel.CATEGORIES)"))
        assertTrue(source.contains("TvLiveBrowseLevel.CATEGORIES -> Column("))
        assertTrue(source.contains("TvLiveBrowseLevel.CHANNELS -> Column("))
        assertTrue(source.contains("browseLevel = TvLiveBrowseLevel.CHANNELS"))
        assertTrue(source.contains("rememberedChannelId"))
        assertTrue(source.contains("scrollToItem(entryFocusIndex)"))
        assertFalse(source.contains("hasChannels = state.channels.isNotEmpty()"))
    }

    private fun appSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found: $relativeToApp")
    }
}
