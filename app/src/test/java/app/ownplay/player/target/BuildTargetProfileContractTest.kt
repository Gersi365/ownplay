package app.ownplay.player.target

import app.ownplay.player.personalization.AppDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildTargetProfileContractTest {
    @Test
    fun compiledTargetOwnsItsPresentationProfileContract() {
        if (OwnPlayBuildTarget.usesDpad) {
            assertFalse(OwnPlayBuildTarget.supportsTouchInput)
            assertEquals(AppDeviceProfile.ANDROID_TV, OwnPlayBuildTarget.fixedProfile)
            assertTrue(OwnPlayBuildTarget.selectableProfiles.isEmpty())
            assertEquals(
                AppDeviceProfile.ANDROID_TV,
                OwnPlayBuildTarget.resolveProfile(storedProfile = null),
            )
            assertEquals(
                AppDeviceProfile.ANDROID_TV,
                OwnPlayBuildTarget.resolveProfile(AppDeviceProfile.SMARTPHONE),
            )
        } else {
            assertTrue(OwnPlayBuildTarget.supportsTouchInput)
            assertNull(OwnPlayBuildTarget.fixedProfile)
            assertEquals(
                setOf(AppDeviceProfile.SMARTPHONE, AppDeviceProfile.TABLET),
                OwnPlayBuildTarget.selectableProfiles,
            )
            assertEquals(
                AppDeviceProfile.SMARTPHONE,
                OwnPlayBuildTarget.resolveProfile(AppDeviceProfile.SMARTPHONE),
            )
            assertEquals(
                AppDeviceProfile.TABLET,
                OwnPlayBuildTarget.resolveProfile(AppDeviceProfile.TABLET),
            )
            assertNull(OwnPlayBuildTarget.resolveProfile(AppDeviceProfile.ANDROID_TV))
            assertNull(OwnPlayBuildTarget.resolveProfile(AppDeviceProfile.TV_BOX))
        }
    }
}
