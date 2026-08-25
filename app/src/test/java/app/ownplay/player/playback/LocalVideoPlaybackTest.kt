package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoPlaybackTest {
    @Test
    fun requestAcceptsContentUriAndKeepsItOpaque() {
        val request = LocalVideoPlayback.request("content://media/external/video/media/42")
        val resolved = LocalVideoPlayback.resolve(request)

        assertEquals(PlaybackMediaKind.LOCAL_VIDEO, request.mediaKind)
        assertEquals("local-video", request.sourceId)
        assertEquals("content://media/external/video/media/42", request.channelId)
        assertEquals("content://media/external/video/media/42", resolved?.value)
        assertEquals(ResolvedPlaybackOrigin.LOCAL_DOWNLOAD, resolved?.origin)
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

    @Test
    fun platformFallbackIsLimitedToUnsupportedMedia() {
        assertTrue(
            LocalVideoPlayback.shouldUsePlatformFallback(
                PlaybackFailure(PlaybackFailureCategory.UNSUPPORTED_MEDIA),
            ),
        )
        assertFalse(
            LocalVideoPlayback.shouldUsePlatformFallback(
                PlaybackFailure(PlaybackFailureCategory.STREAM_UNAVAILABLE),
            ),
        )
        assertFalse(
            LocalVideoPlayback.shouldUsePlatformFallback(
                PlaybackFailure(PlaybackFailureCategory.NETWORK_UNAVAILABLE),
            ),
        )
        assertFalse(
            LocalVideoPlayback.shouldUsePlatformFallback(
                PlaybackFailure(PlaybackFailureCategory.AUTHENTICATION_FAILURE),
            ),
        )
    }

    @Test
    fun platformCacheKeepsReserveAndPreservesSafeExtension() {
        val oneGiB = 1024L * 1024L * 1024L
        assertEquals(oneGiB - 64L * 1024L * 1024L, LocalVideoPlayback.platformCacheBudget(oneGiB))
        assertEquals(0L, LocalVideoPlayback.platformCacheBudget(32L * 1024L * 1024L))
        assertEquals(".mpg", LocalVideoPlayback.platformCacheSuffix("Gersi.MPG"))
        assertEquals(".video", LocalVideoPlayback.platformCacheSuffix("movie"))
        assertEquals(".video", LocalVideoPlayback.platformCacheSuffix("movie.bad-extension-too-long"))
    }
}
