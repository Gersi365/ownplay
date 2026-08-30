package app.ownplay.player.personalization

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.player.target.OwnPlayBuildTarget
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

enum class AppDeviceProfile(
    val storedValue: String,
) {
    SMARTPHONE("smartphone"),
    TABLET("tablet"),
    ANDROID_TV("android_tv"),
    TV_BOX("tv_box");

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
            val storedProfile = AppDeviceProfile.fromStoredOrNull(preferences[DEVICE_PROFILE_KEY])
            val profile = OwnPlayBuildTarget.resolveProfile(storedProfile)
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
        if (
            OwnPlayBuildTarget.fixedProfile != null ||
            profile !in OwnPlayBuildTarget.selectableProfiles
        ) {
            return false
        }
        if (profile == AppDeviceProfile.SMARTPHONE && smartphoneOrientation == null) {
            return false
        }
        return editSafely { preferences ->
            preferences[DEVICE_PROFILE_KEY] = profile.storedValue
            if (profile == AppDeviceProfile.SMARTPHONE) {
                preferences[ORIENTATION_KEY] = smartphoneOrientation!!.storedValue
            }
        }
    }

    suspend fun setProfile(profile: AppDeviceProfile): Boolean {
        if (
            OwnPlayBuildTarget.fixedProfile != null ||
            profile !in OwnPlayBuildTarget.selectableProfiles
        ) {
            return false
        }
        return editSafely { preferences ->
            preferences[DEVICE_PROFILE_KEY] = profile.storedValue
        }
    }

    suspend fun setSmartphoneOrientation(mode: AppOrientationMode): Boolean {
        if (OwnPlayBuildTarget.fixedProfile != null) return false
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
