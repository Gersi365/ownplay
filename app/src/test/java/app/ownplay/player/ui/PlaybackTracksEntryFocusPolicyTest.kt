package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackAudioSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTracksEntryFocusPolicyTest {
    @Test
    fun `default audio selection focuses default audio`() {
        assertEquals(
            PlaybackTracksEntryTarget.DefaultAudio,
            playbackTracksEntryTarget(
                audioSelection = PlaybackAudioSelection.Default,
                supportedAudioTrackIds = setOf("audio-1"),
            ),
        )
    }

    @Test
    fun `supported specific audio focuses the selected audio row`() {
        assertEquals(
            PlaybackTracksEntryTarget.SpecificAudio("audio-2"),
            playbackTracksEntryTarget(
                audioSelection = PlaybackAudioSelection.Specific("audio-2"),
                supportedAudioTrackIds = setOf("audio-1", "audio-2"),
            ),
        )
    }

    @Test
    fun `missing specific audio falls back to default`() {
        assertEquals(
            PlaybackTracksEntryTarget.DefaultAudio,
            playbackTracksEntryTarget(
                audioSelection = PlaybackAudioSelection.Specific("missing"),
                supportedAudioTrackIds = setOf("audio-1"),
            ),
        )
    }

    @Test
    fun `unsupported specific audio falls back to default`() {
        assertEquals(
            PlaybackTracksEntryTarget.DefaultAudio,
            playbackTracksEntryTarget(
                audioSelection = PlaybackAudioSelection.Specific("audio-2"),
                supportedAudioTrackIds = setOf("audio-1"),
            ),
        )
    }
}
