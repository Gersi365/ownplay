package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementRemoteOrderingContractTest {
    private val source by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )
    }

    @Test
    fun `tv live management does not expose pointer drag ordering`() {
        assertFalse("TV management must not expose pointer input.", "pointerInput" in source)
        assertFalse("TV management must not expose drag gesture handlers.", "detectDragGestures" in source)
        assertFalse("TV management must not expose drag handles.", "showDragHandle" in source)
    }

    @Test
    fun `tv live management retains explicit remote channel ordering actions`() {
        assertTrue("TV must expose move-to-top.", "\"Move to top\"" in source)
        assertTrue("TV must expose move-up.", "\"Move up\"" in source)
        assertTrue("TV must expose move-down.", "\"Move down\"" in source)
        assertTrue("TV must expose move-to-bottom.", "\"Move to bottom\"" in source)
        assertTrue(
            "TV ordering must persist through the established manual-order mutation path.",
            "runtime.moveChannelRelative(" in source,
        )
    }

    @Test
    fun `tv category reorder disables unreachable edge moves`() {
        assertTrue("First category move-up must be disabled.", "enabled = index > 0" in source)
        assertTrue(
            "Last category move-down must be disabled.",
            "enabled = index < working.lastIndex" in source,
        )
        assertTrue(
            "Valid category moves must use explicit remote actions.",
            "move(index, -1)" in source && "move(index, 1)" in source,
        )
    }

    @Test
    fun `tv channel selection uses one row focus target`() {
        assertFalse("TV channel rows must not expose a second checkbox focus target.", "Checkbox(" in source)
        assertTrue(
            "The channel row must own selection activation.",
            "onClick = { onChannelSelectionToggle(channel.channelId) }" in source,
        )
        assertTrue("Channel rows must keep fixed geometry.", ".height(64.dp)" in source)
    }

    @Test
    fun `tv remote order disables unreachable selected channel moves`() {
        assertTrue(
            "Move-to-top and move-up must both require an upward move.",
            source.split("enabled = canMoveSelectedUp").size - 1 >= 2,
        )
        assertTrue(
            "Move-down and move-to-bottom must both require a downward move.",
            source.split("enabled = canMoveSelectedDown").size - 1 >= 2,
        )
        assertTrue(
            "Mutation helpers must keep explicit edge guards.",
            source.split("if (!canMoveSelectedUp) return").size - 1 >= 2 &&
                source.split("if (!canMoveSelectedDown) return").size - 1 >= 2,
        )
    }
}
