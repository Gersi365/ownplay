package app.ownplay.player

import app.ownplay.player.playback.PlaybackMediaKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOriginPresentationGateTest {
    @Test
    fun `movie and series fullscreen playback show origin outside pip`() {
        assertTrue(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = PlaybackMediaKind.MOVIE,
                playbackFullscreen = true,
                inPictureInPicture = false,
            ),
        )
        assertTrue(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
                playbackFullscreen = true,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun `live playback never uses vod series origin badge`() {
        assertFalse(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = PlaybackMediaKind.LIVE,
                playbackFullscreen = true,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun `origin badge stays hidden outside fullscreen and in pip`() {
        assertFalse(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = PlaybackMediaKind.MOVIE,
                playbackFullscreen = false,
                inPictureInPicture = false,
            ),
        )
        assertFalse(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = PlaybackMediaKind.SERIES_EPISODE,
                playbackFullscreen = true,
                inPictureInPicture = true,
            ),
        )
        assertFalse(
            shouldShowVodSeriesPlaybackOriginBadge(
                mediaKind = null,
                playbackFullscreen = true,
                inPictureInPicture = false,
            ),
        )
    }
}
