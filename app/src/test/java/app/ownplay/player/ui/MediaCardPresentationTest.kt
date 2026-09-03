package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCardPresentationTest {
    @Test
    fun `focused card wins over selected state`() {
        assertEquals(
            MediaCardVisualState.FOCUSED,
            mediaCardVisualState(focused = true, selected = true),
        )
    }

    @Test
    fun `selected card is selected when not focused`() {
        assertEquals(
            MediaCardVisualState.SELECTED,
            mediaCardVisualState(focused = false, selected = true),
        )
    }

    @Test
    fun `unfocused unselected card stays default`() {
        assertEquals(
            MediaCardVisualState.DEFAULT,
            mediaCardVisualState(focused = false, selected = false),
        )
    }
}
