package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementRemoteOrderingContractTest {
    @Test
    fun `tv live management does not expose pointer drag ordering`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Live browse must identify the television form factor before enabling pointer drag.",
            "configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION" in source,
        )
        assertTrue(
            "Pointer drag must remain disabled on television even while manual ordering is active.",
            "dragEnabled = (manualDragEnabled || favoriteDragEnabled) && !isTelevision" in source,
        )
        assertTrue(
            "The channel drag handle must follow the pointer-drag gate.",
            "showDragHandle = dragEnabled" in source,
        )
        assertTrue(
            "The hold-and-drag instruction must only render when pointer drag is available.",
            "if (dragEnabled) { Text(" in source &&
                "Hold a channel, then drag to reorder My Order." in source,
        )
    }

    @Test
    fun `tv live management retains remote usable ordering actions`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Manual ordering must retain a remote-usable move-to-top action.",
            "ChannelBulkAction.MoveToTop" in source,
        )
        assertTrue(
            "Manual ordering must retain a remote-usable move-to-bottom action.",
            "ChannelBulkAction.MoveToBottom" in source,
        )
        assertTrue(
            "Favorite ordering must retain the equivalent top and bottom actions.",
            "ChannelBulkAction.MoveFavoritesToTop" in source &&
                "ChannelBulkAction.MoveFavoritesToBottom" in source,
        )
    }
}
