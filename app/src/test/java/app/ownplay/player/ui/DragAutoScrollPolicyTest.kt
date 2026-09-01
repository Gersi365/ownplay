package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DragAutoScrollPolicyTest {
    @Test
    fun scrollsBackwardNearTopEdge() {
        assertEquals(
            -32f,
            DragAutoScrollPolicy.delta(
                pointerY = 50f,
                viewportStart = 0,
                viewportEnd = 600,
                edgeSize = 72f,
                step = 32f,
            ),
        )
    }

    @Test
    fun scrollsForwardNearBottomEdge() {
        assertEquals(
            32f,
            DragAutoScrollPolicy.delta(
                pointerY = 560f,
                viewportStart = 0,
                viewportEnd = 600,
                edgeSize = 72f,
                step = 32f,
            ),
        )
    }

    @Test
    fun doesNotScrollInsideSafeViewport() {
        assertEquals(
            0f,
            DragAutoScrollPolicy.delta(
                pointerY = 300f,
                viewportStart = 0,
                viewportEnd = 600,
                edgeSize = 72f,
                step = 32f,
            ),
        )
    }

    @Test
    fun invalidViewportReturnsZero() {
        assertEquals(
            0f,
            DragAutoScrollPolicy.delta(
                pointerY = 10f,
                viewportStart = 100,
                viewportEnd = 100,
                edgeSize = 72f,
                step = 32f,
            ),
        )
    }
}
