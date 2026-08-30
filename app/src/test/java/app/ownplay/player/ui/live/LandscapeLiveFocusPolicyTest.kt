package app.ownplay.player.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeLiveFocusPolicyTest {
    @Test
    fun `tv browser consumes right while preview is open`() {
        assertTrue(
            LandscapeLiveFocusPolicy.consumeBrowserRight(
                isTelevision = true,
                hasPreview = true,
            ),
        )
        assertFalse(
            LandscapeLiveFocusPolicy.consumeBrowserRight(
                isTelevision = true,
                hasPreview = false,
            ),
        )
        assertFalse(
            LandscapeLiveFocusPolicy.consumeBrowserRight(
                isTelevision = false,
                hasPreview = true,
            ),
        )
    }

    @Test
    fun `browser right enters preview only when a channel is selected`() {
        assertEquals(
            LandscapeLiveFocusZone.PREVIEW,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.BROWSER,
                action = LandscapeLiveFocusAction.RIGHT,
                hasPreview = true,
            ),
        )
        assertNull(
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.BROWSER,
                action = LandscapeLiveFocusAction.RIGHT,
                hasPreview = false,
            ),
        )
    }

    @Test
    fun `preview left and back return to browser while down enters epg`() {
        assertEquals(
            LandscapeLiveFocusZone.BROWSER,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.PREVIEW,
                action = LandscapeLiveFocusAction.LEFT,
                hasPreview = true,
            ),
        )
        assertEquals(
            LandscapeLiveFocusZone.BROWSER,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.PREVIEW,
                action = LandscapeLiveFocusAction.BACK,
                hasPreview = true,
            ),
        )
        assertEquals(
            LandscapeLiveFocusZone.EPG,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.PREVIEW,
                action = LandscapeLiveFocusAction.DOWN,
                hasPreview = true,
            ),
        )
    }

    @Test
    fun `epg up returns to preview and left or back returns to browser`() {
        assertEquals(
            LandscapeLiveFocusZone.PREVIEW,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.EPG,
                action = LandscapeLiveFocusAction.UP,
                hasPreview = true,
            ),
        )
        assertEquals(
            LandscapeLiveFocusZone.BROWSER,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.EPG,
                action = LandscapeLiveFocusAction.LEFT,
                hasPreview = true,
            ),
        )
        assertEquals(
            LandscapeLiveFocusZone.BROWSER,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.EPG,
                action = LandscapeLiveFocusAction.BACK,
                hasPreview = true,
            ),
        )
    }

    @Test
    fun `unhandled directions remain available to child controls`() {
        assertNull(
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.BROWSER,
                action = LandscapeLiveFocusAction.DOWN,
                hasPreview = true,
            ),
        )
        assertNull(
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.PREVIEW,
                action = LandscapeLiveFocusAction.RIGHT,
                hasPreview = true,
            ),
        )
        assertNull(
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.EPG,
                action = LandscapeLiveFocusAction.DOWN,
                hasPreview = true,
            ),
        )
    }
}
