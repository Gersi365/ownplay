package app.ownplay.player.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBrowseHierarchyPolicyTest {
    @Test
    fun `back closes preview before changing browse hierarchy`() {
        assertEquals(
            LiveBrowseBackAction.CLOSE_PREVIEW,
            LiveBrowseHierarchyPolicy.backAction(
                hasPreview = true,
                level = LiveBrowseHierarchyLevel.CHANNELS,
            ),
        )
        assertEquals(
            LiveBrowseBackAction.CLOSE_PREVIEW,
            LiveBrowseHierarchyPolicy.backAction(
                hasPreview = true,
                level = LiveBrowseHierarchyLevel.CATEGORIES,
            ),
        )
    }

    @Test
    fun `back returns channel browsing to categories when preview is closed`() {
        assertEquals(
            LiveBrowseBackAction.SHOW_CATEGORIES,
            LiveBrowseHierarchyPolicy.backAction(
                hasPreview = false,
                level = LiveBrowseHierarchyLevel.CHANNELS,
            ),
        )
    }

    @Test
    fun `back propagates from category root when preview is closed`() {
        assertEquals(
            LiveBrowseBackAction.PROPAGATE,
            LiveBrowseHierarchyPolicy.backAction(
                hasPreview = false,
                level = LiveBrowseHierarchyLevel.CATEGORIES,
            ),
        )
    }

    @Test
    fun `first tv ok opens preview and second ok on same channel opens fullscreen`() {
        assertEquals(
            LiveChannelActivationAction.OPEN_PREVIEW,
            LiveBrowseHierarchyPolicy.channelActivationAction(
                isTelevision = true,
                activePreviewChannelId = null,
                activatedChannelId = "channel-7",
            ),
        )
        assertEquals(
            LiveChannelActivationAction.OPEN_FULLSCREEN,
            LiveBrowseHierarchyPolicy.channelActivationAction(
                isTelevision = true,
                activePreviewChannelId = "channel-7",
                activatedChannelId = "channel-7",
            ),
        )
    }

    @Test
    fun `tv ok on another channel replaces preview instead of opening fullscreen`() {
        assertEquals(
            LiveChannelActivationAction.OPEN_PREVIEW,
            LiveBrowseHierarchyPolicy.channelActivationAction(
                isTelevision = true,
                activePreviewChannelId = "channel-7",
                activatedChannelId = "channel-8",
            ),
        )
    }

    @Test
    fun `non tv repeated activation remains preview behavior`() {
        assertEquals(
            LiveChannelActivationAction.OPEN_PREVIEW,
            LiveBrowseHierarchyPolicy.channelActivationAction(
                isTelevision = false,
                activePreviewChannelId = "channel-7",
                activatedChannelId = "channel-7",
            ),
        )
    }
}
