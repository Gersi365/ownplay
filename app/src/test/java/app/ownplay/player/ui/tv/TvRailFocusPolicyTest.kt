package app.ownplay.player.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvRailFocusPolicyTest {
    @Test
    fun railRightEntersContentWhenContentExists() {
        assertEquals(
            TvRailFocusZone.CONTENT,
            TvRailFocusPolicy.destination(
                current = TvRailFocusZone.RAIL,
                action = TvRailFocusAction.RIGHT,
                hasContent = true,
                hasDetail = false,
            ),
        )
    }

    @Test
    fun contentLeftReturnsToRail() {
        assertEquals(
            TvRailFocusZone.RAIL,
            TvRailFocusPolicy.destination(
                current = TvRailFocusZone.CONTENT,
                action = TvRailFocusAction.LEFT,
                hasContent = true,
                hasDetail = false,
            ),
        )
    }

    @Test
    fun contentRightEntersDetailOnlyWhenDetailExists() {
        assertEquals(
            TvRailFocusZone.DETAIL,
            TvRailFocusPolicy.destination(
                current = TvRailFocusZone.CONTENT,
                action = TvRailFocusAction.RIGHT,
                hasContent = true,
                hasDetail = true,
            ),
        )
        assertNull(
            TvRailFocusPolicy.destination(
                current = TvRailFocusZone.CONTENT,
                action = TvRailFocusAction.RIGHT,
                hasContent = true,
                hasDetail = false,
            ),
        )
    }

    @Test
    fun detailLeftAndBackReturnToContent() {
        listOf(TvRailFocusAction.LEFT, TvRailFocusAction.BACK).forEach { action ->
            assertEquals(
                TvRailFocusZone.CONTENT,
                TvRailFocusPolicy.destination(
                    current = TvRailFocusZone.DETAIL,
                    action = action,
                    hasContent = true,
                    hasDetail = true,
                ),
            )
        }
    }

    @Test
    fun railDoesNotLeaveWhenContentIsEmpty() {
        assertNull(
            TvRailFocusPolicy.destination(
                current = TvRailFocusZone.RAIL,
                action = TvRailFocusAction.RIGHT,
                hasContent = false,
                hasDetail = false,
            ),
        )
    }
}
