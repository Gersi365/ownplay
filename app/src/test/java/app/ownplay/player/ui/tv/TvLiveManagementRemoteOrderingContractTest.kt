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

    @Test
    fun `tv category reorder disables unreachable edge moves`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/CategoryReorderSheet.kt"),
        )

        assertTrue(
            "The first category must not expose an enabled move-up action.",
            "enabled = index > 0" in source,
        )
        assertTrue(
            "The last category must not expose an enabled move-down action.",
            "enabled = index < working.lastIndex" in source,
        )
        assertTrue(
            "Valid category moves must continue using the remote ordering path.",
            "moveWithRemote(index, -1)" in source &&
                "moveWithRemote(index, 1)" in source,
        )
    }

    @Test
    fun `tv channel edit rows expose one selection focus target`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Live channel rows must receive the television form factor before configuring selection focus.",
            "isTelevision = isTelevision" in source,
        )
        assertTrue(
            "The TV checkbox must remain outside D-pad focus traversal so the row owns selection focus.",
            "Modifier.focusProperties { canFocus = false }" in source,
        )
        assertTrue(
            "The channel row must continue to own the selection activation path.",
            "if (isEditing) onSelectionToggle() else onClick()" in source,
        )
    }

    @Test
    fun `tv remote order disables unreachable selected channel moves`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Move-to-top and move-up must both require an upward move.",
            source.split("enabled = canMoveSelectedUp").size - 1 >= 2,
        )
        assertTrue(
            "Move-down and move-to-bottom must both require a downward move.",
            source.split("enabled = canMoveSelectedDown").size - 1 >= 2,
        )
        assertTrue(
            "Remote ordering must keep explicit edge guards in the mutation helpers.",
            "if (!canMoveSelectedUp) return" in source &&
                "if (!canMoveSelectedDown) return" in source,
        )
    }
}
