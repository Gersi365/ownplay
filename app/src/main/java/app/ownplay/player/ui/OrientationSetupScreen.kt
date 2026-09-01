package app.ownplay.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.ownplay.player.BuildConfig
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppOrientationMode

@Composable
internal fun OrientationSetupLoadingSurface() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Legacy recovery entry point for an unexpectedly unconfigured profile store.
 *
 * Mobile and TV are separate compile-time targets, so a runtime device-target chooser must never
 * be shown. Recover deterministically to the current build target and keep the user on the loading
 * surface until the persisted target state is repaired.
 */
@Composable
internal fun DeviceProfileSetupScreen(
    onConfigured: (profile: AppDeviceProfile, smartphoneOrientation: AppOrientationMode?) -> Unit,
) {
    LaunchedEffect(Unit) {
        val targetProfile = if (BuildConfig.IS_TV_BUILD) {
            AppDeviceProfile.ANDROID_TV
        } else {
            AppDeviceProfile.SMARTPHONE
        }
        onConfigured(
            targetProfile,
            if (targetProfile == AppDeviceProfile.SMARTPHONE) {
                AppOrientationMode.PORTRAIT
            } else {
                null
            },
        )
    }
    OrientationSetupLoadingSurface()
}
