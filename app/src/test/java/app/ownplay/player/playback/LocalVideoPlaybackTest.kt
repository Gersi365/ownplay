package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalVideoPlaybackTest {
    @Test
    fun requestAcceptsContentUriAndKeepsItOpaque() {
        val request = LocalVideoPlayback.request("content://media/external/video/media/42")

        assertEquals(PlaybackMediaKind.LOCAL_VIDEO, request.mediaKind)
        assertEquals("local-video", request.sourceId)
        assertEquals("content://media/external/video/media/42", request.channelId)
        assertEquals(
            "content://media/external/video/media/42",
            LocalVideoPlayback.resolve(request)?.value,
        )
    }

    @Test
    fun requestRejectsFileAndRemoteUris() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalVideoPlayback.request("file:///sdcard/Movies/test.mp4")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalVideoPlayback.request("https://example.com/test.mp4")
        }
    }

    @Test
    fun resolverIgnoresNonLocalRequests() {
        val request = PlaybackRequest(
            sourceId = "source",
            channelId = "channel",
            mediaKind = PlaybackMediaKind.LIVE,
        )

        assertNull(LocalVideoPlayback.resolve(request))
    }
}
