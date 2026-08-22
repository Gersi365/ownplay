package app.ownplay.player.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class Media3PlaybackFailureMapperTest {
    @Test
    fun networkAndTimeoutCodesMapDeterministically() {
        assertEquals(
            PlaybackFailureCategory.NETWORK_UNAVAILABLE,
            Media3PlaybackFailureMapper.map(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ).category,
        )
        assertEquals(
            PlaybackFailureCategory.TIMEOUT,
            Media3PlaybackFailureMapper.map(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ).category,
        )
    }

    @Test
    fun authenticationRequiresExplicitHttpStatusEvidence() {
        assertEquals(
            PlaybackFailureCategory.AUTHENTICATION_FAILURE,
            Media3PlaybackFailureMapper.map(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 401,
            ).category,
        )
        assertEquals(
            PlaybackFailureCategory.AUTHENTICATION_FAILURE,
            Media3PlaybackFailureMapper.map(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 403,
            ).category,
        )
        assertEquals(
            PlaybackFailureCategory.STREAM_UNAVAILABLE,
            Media3PlaybackFailureMapper.map(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 500,
            ).category,
        )
        assertEquals(
            PlaybackFailureCategory.STREAM_UNAVAILABLE,
            Media3PlaybackFailureMapper.map(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = null,
            ).category,
        )
    }

    @Test
    fun unsupportedMediaCodesMapWithoutFreeFormErrorText() {
        assertEquals(
            PlaybackFailureCategory.UNSUPPORTED_MEDIA,
            Media3PlaybackFailureMapper.map(
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            ).category,
        )
        assertEquals(
            PlaybackFailureCategory.UNSUPPORTED_MEDIA,
            Media3PlaybackFailureMapper.map(
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ).category,
        )
    }
}
