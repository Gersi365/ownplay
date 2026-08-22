package app.ownplay.player.playback

import app.ownplay.player.live.LiveChannelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackFlowTest {
    @Test
    fun normalChannelTapCreatesOpaquePlaybackRequest() {
        val channel = channel(
            sourceId = "source-secret-identity",
            channelId = "channel-secret-identity",
            displayName = "News",
        )

        val action = LiveChannelSelectionRouter.route(
            channel = channel,
            isEditing = false,
        ) as LiveChannelSelectionAction.StartPlayback

        assertEquals("source-secret-identity", action.selection.request.sourceId)
        assertEquals("channel-secret-identity", action.selection.request.channelId)
        assertEquals(PlaybackMediaKind.LIVE, action.selection.request.mediaKind)
        assertEquals("News", action.selection.displayName)

        val rendered = action.selection.toString()
        assertFalse(rendered.contains("source-secret-identity"))
        assertFalse(rendered.contains("channel-secret-identity"))
        assertFalse(rendered.contains("http://"))
        assertFalse(rendered.contains("https://"))
    }

    @Test
    fun editModeTapNeverCreatesPlaybackRequest() {
        val action = LiveChannelSelectionRouter.route(
            channel = channel(),
            isEditing = true,
        )

        assertTrue(action is LiveChannelSelectionAction.ToggleEditSelection)
        assertFalse(action is LiveChannelSelectionAction.StartPlayback)
    }

    @Test
    fun selectedPlaybackIdentityRemainsStableAfterBrowseItemChanges() {
        val original = channel(
            sourceId = "source-a",
            channelId = "channel-a",
            displayName = "Original name",
        )
        val selection = LivePlaybackSelection.from(original)

        val changedBrowseItem = original.copy(
            sourceId = "source-b",
            channelId = "channel-b",
            displayName = "Changed name",
        )

        assertEquals("source-a", selection.request.sourceId)
        assertEquals("channel-a", selection.request.channelId)
        assertEquals("Original name", selection.displayName)
        assertEquals("source-b", changedBrowseItem.sourceId)
        assertEquals("channel-b", changedBrowseItem.channelId)
    }

    @Test
    fun blankDisplayNameFallsBackWithoutChangingIdentity() {
        val selection = LivePlaybackSelection.from(
            channel(
                sourceId = "source-a",
                channelId = "channel-a",
                displayName = "   ",
            ),
        )

        assertEquals("Live channel", selection.displayName)
        assertEquals("source-a", selection.request.sourceId)
        assertEquals("channel-a", selection.request.channelId)
    }

    private fun channel(
        sourceId: String = "source",
        channelId: String = "channel",
        displayName: String = "Channel",
    ): LiveChannelItem = LiveChannelItem(
        channelId = channelId,
        sourceId = sourceId,
        categoryKey = null,
        categoryName = null,
        providerName = displayName,
        localDisplayName = null,
        displayName = displayName,
        logoRef = null,
        hasLogoOverride = false,
        providerOrder = 0L,
        manualOrder = null,
        favoriteOrder = null,
        isFavorite = false,
        isHidden = false,
        availability = "available",
        recentAtEpochMillis = null,
    )
}
