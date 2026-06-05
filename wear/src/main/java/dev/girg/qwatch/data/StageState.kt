package dev.girg.qwatch.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object StageStateKeys {
    val STAGE_ID = stringPreferencesKey("stage_id")
    val STAGE_NAME = stringPreferencesKey("stage_name")
    val ARTIST_NAME = stringPreferencesKey("artist_name")
    val IS_GPS_AVAILABLE = booleanPreferencesKey("is_gps_available")
    val IS_FESTIVAL_ACTIVE = booleanPreferencesKey("is_festival_active")
    val LAST_UPDATE_MILLIS = longPreferencesKey("last_update_millis")
}

data class StageState(
    val stageId: String? = null,
    val stageName: String? = null,
    val artistName: String? = null,
    val isGpsAvailable: Boolean = false,
    val isFestivalActive: Boolean = false,
    val lastUpdateMillis: Long = 0L
)
