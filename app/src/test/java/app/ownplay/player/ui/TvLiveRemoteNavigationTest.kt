package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackNavigationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLiveRemoteNavigationTest {
    @Test
    fun upMovesToPreviousChannelOnKeyDown() {
        assertEquals(
            PlaybackNavigationDirection.PREVIOUS,
            tvLiveRemoteNavigation(TvLiveRemoteArrow.UP, keyDown = true),
        )
    }

    @Test
    fun downMovesToNextChannelOnKeyDown() {
        assertEquals(
            PlaybackNavigationDirection.NEXT,
            tvLiveRemoteNavigation(TvLiveRemoteArrow.DOWN, keyDown = true),
        )
    }

    @Test
    fun keyUpDoesNotNavigate() {
        assertNull(tvLiveRemoteNavigation(TvLiveRemoteArrow.UP, keyDown = false))
        assertNull(tvLiveRemoteNavigation(TvLiveRemoteArrow.DOWN, keyDown = false))
    }

    @Test
    fun unrelatedArrowDoesNotNavigate() {
        assertNull(tvLiveRemoteNavigation(TvLiveRemoteArrow.OTHER, keyDown = true))
    }
}
