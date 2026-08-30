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

    @Test
    fun controlsMenusDiagnosticsAndDialogsOwnTheDpadWhileVisible() {
        listOf(
            TvLiveRemoteInputOwner.PLAYBACK_CONTROLS,
            TvLiveRemoteInputOwner.TRACK_SELECTION,
            TvLiveRemoteInputOwner.DIAGNOSTICS,
            TvLiveRemoteInputOwner.DIALOG,
        ).forEach { owner ->
            assertNull(
                "Channel switching must stay dormant while $owner owns D-pad input",
                tvLiveRemoteNavigation(
                    arrow = TvLiveRemoteArrow.UP,
                    keyDown = true,
                    inputOwner = owner,
                ),
            )
            assertNull(
                tvLiveRemoteNavigation(
                    arrow = TvLiveRemoteArrow.DOWN,
                    keyDown = true,
                    inputOwner = owner,
                ),
            )
        }
    }
}
