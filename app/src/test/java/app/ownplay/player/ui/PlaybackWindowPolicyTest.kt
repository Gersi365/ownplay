package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWindowPolicyTest {
    @Test
    fun pipEligibilityRequiresSupportAndActivePlayback() {
        assertTrue(
            PlaybackWindowPolicy.isPipEligible(
                pipSupported = true,
                isPlaying = true,
            ),
        )
        assertFalse(
            PlaybackWindowPolicy.isPipEligible(
                pipSupported = false,
                isPlaying = true,
            ),
        )
        assertFalse(
            PlaybackWindowPolicy.isPipEligible(
                pipSupported = true,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun phoneAppShellRequestsPortrait() {
        assertEquals(
            PlaybackOrientationIntent.PORTRAIT,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
                smallestScreenWidthDp = 411,
            ),
        )
    }

    @Test
    fun phoneFullscreenRequestsSensorLandscape() {
        assertEquals(
            PlaybackOrientationIntent.SENSOR_LANDSCAPE,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = false,
                smallestScreenWidthDp = 411,
            ),
        )
    }

    @Test
    fun largeScreenFollowsSystem() {
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
                smallestScreenWidthDp = 600,
            ),
        )
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = false,
                smallestScreenWidthDp = 600,
            ),
        )
    }

    @Test
    fun unknownWidthFollowsSystem() {
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
                smallestScreenWidthDp = 0,
            ),
        )
    }

    @Test
    fun pictureInPictureAlwaysReleasesForcedOrientation() {
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = true,
                smallestScreenWidthDp = 411,
            ),
        )
    }

    @Test
    fun leavingFullscreenReturnsPhoneToPortrait() {
        assertEquals(
            PlaybackOrientationIntent.PORTRAIT,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
                smallestScreenWidthDp = 411,
            ),
        )
    }
}
