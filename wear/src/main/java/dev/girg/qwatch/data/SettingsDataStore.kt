package dev.girg.qwatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// App settings kept separate from the live stage_state store.
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

private val SMART_MODE = booleanPreferencesKey("smart_mode")

/** True = adaptive battery-saving loop, false = continuous high-accuracy "dumb" stream. Defaults to smart. */
fun Context.readSmartModeFlow(): Flow<Boolean> =
    appSettingsDataStore.data.map { prefs -> prefs[SMART_MODE] ?: true }

suspend fun Context.writeSmartMode(smart: Boolean) {
    appSettingsDataStore.edit { prefs -> prefs[SMART_MODE] = smart }
}
