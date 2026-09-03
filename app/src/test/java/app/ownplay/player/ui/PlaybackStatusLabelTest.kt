package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackFailure
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusLabelTest {
    private val request = PlaybackRequest(
        sourceId = "source",
        channelId = "channel",
    )

    @Test
    fun bufferingPlayingStateHasDedicatedLabel() {
        assertEquals(
            "Buffering…",
            playbackStatusLabel(PlaybackState.Playing(request = request, buffering = true)),
        )
        assertEquals(
            "Playing",
            playbackStatusLabel(PlaybackState.Playing(request = request, buffering = false)),
        )
    }

    @Test
    fun failuresUseSpecificUserFacingLabels() {
        val expectations = mapOf(
            PlaybackFailureCategory.NETWORK_UNAVAILABLE to "Network unavailable",
            PlaybackFailureCategory.TIMEOUT to "Playback timed out",
            PlaybackFailureCategory.AUTHENTICATION_FAILURE to "Authentication failed",
            PlaybackFailureCategory.STREAM_UNAVAILABLE to "Stream unavailable",
            PlaybackFailureCategory.UNSUPPORTED_MEDIA to "Unsupported media",
            PlaybackFailureCategory.UNKNOWN to "Playback failed",
        )

        expectations.forEach { (category, expected) ->
            val state = PlaybackState.Failed(
                request = request,
                failure = PlaybackFailure(category),
            )
            assertEquals(expected, playbackStatusLabel(state))
        }
    }

    @Test
    fun failuresExposeCategorySpecificActionHints() {
        val expectations = mapOf(
            PlaybackFailureCategory.NETWORK_UNAVAILABLE to
                "Check the network connection and try again.",
            PlaybackFailureCategory.TIMEOUT to
                "The stream did not respond in time. Try again.",
            PlaybackFailureCategory.AUTHENTICATION_FAILURE to
                "The provider rejected access to this stream. Check playlist credentials.",
            PlaybackFailureCategory.STREAM_UNAVAILABLE to
                "This stream is currently unavailable from the provider.",
            PlaybackFailureCategory.UNSUPPORTED_MEDIA to
                "This stream format is not supported on this device.",
            PlaybackFailureCategory.UNKNOWN to
                "The stream could not be started.",
        )

        expectations.forEach { (category, expected) ->
            val state = PlaybackState.Failed(
                request = request,
                failure = PlaybackFailure(category),
            )
            assertEquals(expected, playbackFailureHint(state))
        }
    }
}
