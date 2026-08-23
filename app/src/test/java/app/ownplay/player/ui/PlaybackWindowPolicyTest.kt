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
    fun appShellUsesFullSensorOrientation() {
        assertEquals(
            PlaybackOrientationIntent.FULL_SENSOR,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun fullscreenRequestsSensorLandscape() {
        assertEquals(
            PlaybackOrientationIntent.SENSOR_LANDSCAPE,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun pictureInPictureAlwaysFollowsSystem() {
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
    fun leavingFullscreenReturnsToFullSensorOrientation() {
        assertEquals(
            PlaybackOrientationIntent.FULL_SENSOR,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                inPictureInPicture = false,
            ),
        )
    }
}
