package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackFailure
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusLabelTest {
    @Test
    fun insecureTransportHasActionableStatus() {
        val state = PlaybackState.Failed(
            request = PlaybackRequest(sourceId = "source", channelId = "channel"),
            failure = PlaybackFailure(PlaybackFailureCategory.INSECURE_TRANSPORT_BLOCKED),
        )

        assertEquals(
            "Insecure HTTP is blocked for this source",
            playbackStatusLabel(state),
        )
    }

    @Test
    fun failureHelperMatchesStatusLabel() {
        PlaybackFailureCategory.entries.forEach { category ->
            val state = PlaybackState.Failed(
                request = PlaybackRequest(sourceId = "source", channelId = "channel"),
                failure = PlaybackFailure(category),
            )
            assertEquals(playbackFailureLabel(category), playbackStatusLabel(state))
        }
    }
}
