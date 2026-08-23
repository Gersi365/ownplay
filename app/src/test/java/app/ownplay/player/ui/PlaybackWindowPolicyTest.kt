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
    fun normalAppShellIsPortraitFirst() {
        assertEquals(
            PlaybackOrientationIntent.PORTRAIT,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun fullscreenPlaybackRequestsSensorLandscape() {
        assertEquals(
            PlaybackOrientationIntent.SENSOR_LANDSCAPE,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun pictureInPictureReleasesOrientationToSystem() {
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = true,
            ),
        )
        assertEquals(
            PlaybackOrientationIntent.FOLLOW_SYSTEM,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = true,
            ),
        )
    }

    @Test
    fun leavingFullscreenReturnsToPortrait() {
        assertEquals(
            PlaybackOrientationIntent.PORTRAIT,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
            ),
        )
    }
}
