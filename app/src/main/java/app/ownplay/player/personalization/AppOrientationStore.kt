package app.ownplay.player.personalization

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.ownPlayAppPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "ownplay_app_preferences",
)

enum class AppOrientationMode(
    val storedValue: String,
) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun fromStored(value: String?): AppOrientationMode =
            fromStoredOrNull(value) ?: PORTRAIT

        fun fromStoredOrNull(value: String?): AppOrientationMode? =
            entries.firstOrNull { mode -> mode.storedValue == value }
    }
}

enum class AppInputMode {
    TOUCHSCREEN,
    DPAD,
}

enum class AppDeviceProfile(
    val storedValue: String,
    val inputMode: AppInputMode,
) {
    SMARTPHONE("smartphone", AppInputMode.TOUCHSCREEN),
    TABLET("tablet", AppInputMode.TOUCHSCREEN),
    ANDROID_TV("android_tv", AppInputMode.DPAD),
    TV_BOX("tv_box", AppInputMode.DPAD);

    val usesDpad: Boolean
        get() = inputMode == AppInputMode.DPAD

    companion object {
        fun fromStoredOrNull(value: String?): AppDeviceProfile? =
            entries.firstOrNull { profile -> profile.storedValue == value }
    }
}

data class AppDeviceSettings(
    val profile: AppDeviceProfile,
    val smartphoneOrientation: AppOrientationMode,
) {
    val effectiveOrientation: AppOrientationMode
        get() = if (profile == AppDeviceProfile.SMARTPHONE) {
            smartphoneOrientation
        } else {
            AppOrientationMode.LANDSCAPE
        }

    val inputMode: AppInputMode
        get() = profile.inputMode
}

sealed interface AppDeviceProfileSelection {
    data object Loading : AppDeviceProfileSelection
    data object Unconfigured : AppDeviceProfileSelection
    data class Configured(
        val settings: AppDeviceSettings,
    ) : AppDeviceProfileSelection
}

class AppDeviceProfileStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.ownPlayAppPreferences

    fun observeSelection(): Flow<AppDeviceProfileSelection> = safePreferences(dataStore)
        .map { preferences ->
            val profile = AppDeviceProfile.fromStoredOrNull(preferences[DEVICE_PROFILE_KEY])
                ?: return@map AppDeviceProfileSelection.Unconfigured
            AppDeviceProfileSelection.Configured(
                AppDeviceSettings(
                    profile = profile,
                    smartphoneOrientation = AppOrientationMode.fromStored(
                        preferences[ORIENTATION_KEY],
                    ),
                ),
            )
        }

    suspend fun configure(
        profile: AppDeviceProfile,
        smartphoneOrientation: AppOrientationMode? = null,
    ): Boolean {
        if (profile == AppDeviceProfile.SMARTPHONE && smartphoneOrientation == null) {
            return false
        }
        return editSafely { preferences ->
            preferences[DEVICE_PROFILE_KEY] = profile.storedValue
            preferences[ORIENTATION_KEY] = if (profile == AppDeviceProfile.SMARTPHONE) {
                smartphoneOrientation!!.storedValue
            } else {
                AppOrientationMode.LANDSCAPE.storedValue
            }
        }
    }

    suspend fun setProfile(profile: AppDeviceProfile): Boolean = editSafely { preferences ->
        preferences[DEVICE_PROFILE_KEY] = profile.storedValue
        preferences[ORIENTATION_KEY] = if (profile == AppDeviceProfile.SMARTPHONE) {
            AppOrientationMode.fromStored(preferences[ORIENTATION_KEY]).storedValue
        } else {
            AppOrientationMode.LANDSCAPE.storedValue
        }
    }

    suspend fun setSmartphoneOrientation(mode: AppOrientationMode): Boolean {
        var accepted = false
        val persisted = editSafely { preferences ->
            if (
                AppDeviceProfile.fromStoredOrNull(preferences[DEVICE_PROFILE_KEY]) ==
                AppDeviceProfile.SMARTPHONE
            ) {
                preferences[ORIENTATION_KEY] = mode.storedValue
                accepted = true
            }
        }
        return persisted && accepted
    }

    private suspend fun editSafely(block: (MutablePreferences) -> Unit): Boolean = try {
        dataStore.edit { preferences -> block(preferences) }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

sealed interface AppOrientationSelection {
    data object Loading : AppOrientationSelection
    data object Unconfigured : AppOrientationSelection
    data class Configured(
        val mode: AppOrientationMode,
    ) : AppOrientationSelection
}

/**
 * Compatibility facade while device-profile adoption is rolled through the UI.
 * Non-smartphone profiles are always constrained to landscape.
 */
class AppOrientationStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.ownPlayAppPreferences

    fun observe(): Flow<AppOrientationMode> = safePreferences(dataStore)
        .map { preferences -> effectiveOrientation(preferences) }

    fun observeSelection(): Flow<AppOrientationSelection> = safePreferences(dataStore)
        .map { preferences ->
            val mode = AppOrientationMode.fromStoredOrNull(preferences[ORIENTATION_KEY])
            if (mode == null) {
                AppOrientationSelection.Unconfigured
            } else {
                AppOrientationSelection.Configured(effectiveOrientation(preferences))
            }
        }

    suspend fun set(mode: AppOrientationMode): Boolean = try {
        dataStore.edit { preferences ->
            val profile = AppDeviceProfile.fromStoredOrNull(preferences[DEVICE_PROFILE_KEY])
            preferences[ORIENTATION_KEY] = if (
                profile != null && profile != AppDeviceProfile.SMARTPHONE
            ) {
                AppOrientationMode.LANDSCAPE.storedValue
            } else {
                mode.storedValue
            }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun effectiveOrientation(preferences: Preferences): AppOrientationMode {
        val profile = AppDeviceProfile.fromStoredOrNull(preferences[DEVICE_PROFILE_KEY])
        return if (profile != null && profile != AppDeviceProfile.SMARTPHONE) {
            AppOrientationMode.LANDSCAPE
        } else {
            AppOrientationMode.fromStored(preferences[ORIENTATION_KEY])
        }
    }
}

private fun safePreferences(dataStore: DataStore<Preferences>): Flow<Preferences> = dataStore.data
    .catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

private val DEVICE_PROFILE_KEY = stringPreferencesKey("device_profile")
private val ORIENTATION_KEY = stringPreferencesKey("app_orientation")
