package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDetailsStatePanelTest {
    @Test
    fun `playback return wins over details retry`() {
        assertEquals(
            MediaDetailsFocusTarget.PLAYBACK,
            mediaDetailsFocusTarget(
                isTelevision = true,
                playbackReturnRequested = true,
                errorActionAvailable = true,
                backRequested = true,
            ),
        )
    }

    @Test
    fun `details retry wins over hierarchy back`() {
        assertEquals(
            MediaDetailsFocusTarget.RETRY,
            mediaDetailsFocusTarget(
                isTelevision = true,
                playbackReturnRequested = false,
                errorActionAvailable = true,
                backRequested = true,
            ),
        )
    }

    @Test
    fun `hierarchy back is used when there is no playback return or error action`() {
        assertEquals(
            MediaDetailsFocusTarget.BACK,
            mediaDetailsFocusTarget(
                isTelevision = true,
                playbackReturnRequested = false,
                errorActionAvailable = false,
                backRequested = true,
            ),
        )
    }

    @Test
    fun `phone does not request TV focus`() {
        assertEquals(
            MediaDetailsFocusTarget.NONE,
            mediaDetailsFocusTarget(
                isTelevision = false,
                playbackReturnRequested = true,
                errorActionAvailable = true,
                backRequested = true,
            ),
        )
    }
}
