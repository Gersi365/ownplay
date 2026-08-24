package app.ownplay.player.ui

import app.ownplay.player.personalization.AppOrientationMode
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
    fun portraitSettingLocksNormalAppShellToPortrait() {
        assertEquals(
            PlaybackOrientationIntent.PORTRAIT,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                appOrientation = AppOrientationMode.PORTRAIT,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun landscapeSettingLocksNormalAppShellToLandscape() {
        assertEquals(
            PlaybackOrientationIntent.LANDSCAPE,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = false,
                appOrientation = AppOrientationMode.LANDSCAPE,
                inPictureInPicture = false,
            ),
        )
    }

    @Test
    fun fullscreenAlwaysFollowsPhysicalSensor() {
        assertEquals(
            PlaybackOrientationIntent.SENSOR,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                appOrientation = AppOrientationMode.PORTRAIT,
                inPictureInPicture = false,
            ),
        )
        assertEquals(
            PlaybackOrientationIntent.SENSOR,
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = true,
                appOrientation = AppOrientationMode.LANDSCAPE,
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
                appOrientation = AppOrientationMode.LANDSCAPE,
                inPictureInPicture = true,
            ),
        )
    }
}
