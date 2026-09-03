package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackFailure
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureFocusPolicyTest {
    private val request = PlaybackRequest(
        sourceId = "source",
        channelId = "channel",
    )

    @Test
    fun `playing controls auto hide only when visible and no transient panel is open`() {
        val playing = PlaybackState.Playing(request)

        assertTrue(
            playbackControlsShouldAutoHide(
                state = playing,
                controlsVisible = true,
                transientPanelVisible = false,
            ),
        )
        assertFalse(
            playbackControlsShouldAutoHide(
                state = playing,
                controlsVisible = false,
                transientPanelVisible = false,
            ),
        )
        assertFalse(
            playbackControlsShouldAutoHide(
                state = playing,
                controlsVisible = true,
                transientPanelVisible = true,
            ),
        )
    }

    @Test
    fun `failure never participates in controls auto hide`() {
        val failed = PlaybackState.Failed(
            request = request,
            failure = PlaybackFailure(PlaybackFailureCategory.STREAM_UNAVAILABLE),
        )

        assertFalse(
            playbackControlsShouldAutoHide(
                state = failed,
                controlsVisible = true,
                transientPanelVisible = false,
            ),
        )
    }

    @Test
    fun `retryable failure enters on retry`() {
        assertEquals(
            PlaybackFailureEntryAction.RETRY,
            playbackFailureEntryAction(canRetry = true),
        )
    }

    @Test
    fun `non retryable failure enters on back to channels`() {
        assertEquals(
            PlaybackFailureEntryAction.RETURN_TO_CHANNELS,
            playbackFailureEntryAction(canRetry = false),
        )
    }
}
