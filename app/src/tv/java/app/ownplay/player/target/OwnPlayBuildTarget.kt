package app.ownplay.player.target

import app.ownplay.player.personalization.AppDeviceProfile

internal object OwnPlayBuildTarget {
    const val usesDpad: Boolean = true
    const val supportsTouchInput: Boolean = false
    const val supportsPictureInPicture: Boolean = false

    val fixedProfile: AppDeviceProfile? = AppDeviceProfile.ANDROID_TV
    val selectableProfiles: Set<AppDeviceProfile> = emptySet()
}
