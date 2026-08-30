package app.ownplay.player

import android.app.Application
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppDeviceProfileSelection
import app.ownplay.player.personalization.AppDeviceProfileStore
import app.ownplay.player.personalization.AppOrientationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Compile-time device target bootstrap.
 *
 * Mobile and TV are separate APKs. The selected build target owns the device profile instead of
 * asking the user to choose a runtime profile on first launch.
 */
class OwnPlayTargetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val desiredProfile = if (BuildConfig.IS_TV_BUILD) {
            AppDeviceProfile.ANDROID_TV
        } else {
            AppDeviceProfile.SMARTPHONE
        }
        val profileStore = AppDeviceProfileStore(this)
        runBlocking(Dispatchers.IO) {
            when (val selection = profileStore.observeSelection().first()) {
                AppDeviceProfileSelection.Loading -> Unit
                AppDeviceProfileSelection.Unconfigured -> {
                    profileStore.configure(
                        profile = desiredProfile,
                        smartphoneOrientation = if (BuildConfig.IS_TV_BUILD) {
                            null
                        } else {
                            AppOrientationMode.PORTRAIT
                        },
                    )
                }
                is AppDeviceProfileSelection.Configured -> {
                    if (selection.settings.profile != desiredProfile) {
                        profileStore.setProfile(desiredProfile)
                    }
                }
            }
        }
    }
}
