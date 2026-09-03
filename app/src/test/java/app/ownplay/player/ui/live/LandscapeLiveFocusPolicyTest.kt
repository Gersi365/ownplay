package app.ownplay.player.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandscapeLiveFocusPolicyTest {
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
    fun `preview left returns to browser while down enters epg and back stays unhandled`() {
        assertEquals(
            LandscapeLiveFocusZone.BROWSER,
            LandscapeLiveFocusPolicy.destination(
                current = LandscapeLiveFocusZone.PREVIEW,
                action = LandscapeLiveFocusAction.LEFT,
                hasPreview = true,
            ),
        )
        assertNull(
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
    fun `epg up returns to preview and left returns to browser while back stays unhandled`() {
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
        assertNull(
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
