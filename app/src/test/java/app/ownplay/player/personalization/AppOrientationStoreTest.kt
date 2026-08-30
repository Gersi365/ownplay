package app.ownplay.player.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppOrientationStoreTest {
    @Test
    fun missingOrUnknownPreferenceFallsBackToPortrait() {
        assertEquals(AppOrientationMode.PORTRAIT, AppOrientationMode.fromStored(null))
        assertEquals(AppOrientationMode.PORTRAIT, AppOrientationMode.fromStored(""))
        assertEquals(AppOrientationMode.PORTRAIT, AppOrientationMode.fromStored("sensor"))
    }

    @Test
    fun storedLandscapePreferenceRestoresLandscape() {
        assertEquals(
            AppOrientationMode.LANDSCAPE,
            AppOrientationMode.fromStored("landscape"),
        )
    }

    @Test
    fun missingOrUnknownOrientationCanBeDetected() {
        assertNull(AppOrientationMode.fromStoredOrNull(null))
        assertNull(AppOrientationMode.fromStoredOrNull(""))
        assertNull(AppOrientationMode.fromStoredOrNull("sensor"))
    }

    @Test
    fun deviceProfilesRestoreOnlyKnownStoredValues() {
        assertEquals(
            AppDeviceProfile.SMARTPHONE,
            AppDeviceProfile.fromStoredOrNull("smartphone"),
        )
        assertEquals(AppDeviceProfile.TABLET, AppDeviceProfile.fromStoredOrNull("tablet"))
        assertEquals(
            AppDeviceProfile.ANDROID_TV,
            AppDeviceProfile.fromStoredOrNull("android_tv"),
        )
        assertEquals(AppDeviceProfile.TV_BOX, AppDeviceProfile.fromStoredOrNull("tv_box"))
        assertNull(AppDeviceProfile.fromStoredOrNull(null))
        assertNull(AppDeviceProfile.fromStoredOrNull("television"))
    }

    @Test
    fun smartphoneRespectsOrientationWhileOtherProfilesStayLandscape() {
        assertEquals(
            AppOrientationMode.PORTRAIT,
            AppDeviceSettings(
                profile = AppDeviceProfile.SMARTPHONE,
                smartphoneOrientation = AppOrientationMode.PORTRAIT,
            ).effectiveOrientation,
        )
        assertEquals(
            AppOrientationMode.LANDSCAPE,
            AppDeviceSettings(
                profile = AppDeviceProfile.SMARTPHONE,
                smartphoneOrientation = AppOrientationMode.LANDSCAPE,
            ).effectiveOrientation,
        )
        listOf(
            AppDeviceProfile.TABLET,
            AppDeviceProfile.ANDROID_TV,
            AppDeviceProfile.TV_BOX,
        ).forEach { profile ->
            assertEquals(
                AppOrientationMode.LANDSCAPE,
                AppDeviceSettings(
                    profile = profile,
                    smartphoneOrientation = AppOrientationMode.PORTRAIT,
                ).effectiveOrientation,
            )
        }
    }
}
