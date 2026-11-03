package com.mindfullinking.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Local DataStore preferences repository managing metadata fetching toggle and analytics privacy settings.
 */
class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val METADATA_FETCH_ENABLED = booleanPreferencesKey("metadata_fetch_enabled")
        val ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
    }

    /**
     * Flow observing whether automatic metadata fetching (open graph title/preview) is enabled.
     * Defaults to false for strict local privacy.
     */
    val metadataFetchEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.METADATA_FETCH_ENABLED] ?: false
        }

    /**
     * Flow observing analytics opt-in state.
     * Defaults to false for local-first privacy.
     */
    val analyticsOptIn: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.ANALYTICS_OPT_IN] ?: false
        }

    suspend fun setMetadataFetchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.METADATA_FETCH_ENABLED] = enabled
        }
    }

    suspend fun setAnalyticsOptIn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANALYTICS_OPT_IN] = enabled
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
