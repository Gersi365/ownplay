package app.ownplay.player.personalization

import android.content.Context
import androidx.datastore.core.DataStore
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

sealed interface AppOrientationSelection {
    data object Loading : AppOrientationSelection
    data object Unconfigured : AppOrientationSelection
    data class Configured(
        val mode: AppOrientationMode,
    ) : AppOrientationSelection
}

class AppOrientationStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.ownPlayAppPreferences

    fun observe(): Flow<AppOrientationMode> = observePreferences()
        .map { preferences ->
            AppOrientationMode.fromStored(preferences[ORIENTATION_KEY])
        }

    fun observeSelection(): Flow<AppOrientationSelection> = observePreferences()
        .map { preferences ->
            val mode = AppOrientationMode.fromStoredOrNull(preferences[ORIENTATION_KEY])
            if (mode == null) {
                AppOrientationSelection.Unconfigured
            } else {
                AppOrientationSelection.Configured(mode)
            }
        }

    suspend fun set(mode: AppOrientationMode): Boolean = try {
        dataStore.edit { preferences ->
            preferences[ORIENTATION_KEY] = mode.storedValue
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun observePreferences(): Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    private companion object {
        val ORIENTATION_KEY = stringPreferencesKey("app_orientation")
    }
}
