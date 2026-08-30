package app.ownplay.player.target

import app.ownplay.player.personalization.AppDeviceProfile

internal object OwnPlayBuildTarget {
    const val usesDpad: Boolean = false
    const val supportsTouchInput: Boolean = true
    const val supportsPictureInPicture: Boolean = true

    val fixedProfile: AppDeviceProfile? = null
    val selectableProfiles: Set<AppDeviceProfile> = setOf(
        AppDeviceProfile.SMARTPHONE,
        AppDeviceProfile.TABLET,
    )

    fun resolveProfile(storedProfile: AppDeviceProfile?): AppDeviceProfile? =
        storedProfile?.takeIf(selectableProfiles::contains)
}
