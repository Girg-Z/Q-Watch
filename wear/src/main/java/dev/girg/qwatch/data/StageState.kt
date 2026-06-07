package dev.girg.qwatch.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object StageStateKeys {
    val STAGE_ID = stringPreferencesKey("stage_id")
    val STAGE_NAME = stringPreferencesKey("stage_name")
    val ARTIST_NAME = stringPreferencesKey("artist_name")
    val IS_GPS_AVAILABLE = booleanPreferencesKey("is_gps_available")
    val IS_FESTIVAL_ACTIVE = booleanPreferencesKey("is_festival_active")
    val LAST_UPDATE_MILLIS = longPreferencesKey("last_update_millis")
    val SET_PROGRESS_PERCENT = intPreferencesKey("set_progress_percent")
    val MINS_TO_SET_END = intPreferencesKey("mins_to_set_end")
    val NEXT_ARTIST_NAME = stringPreferencesKey("next_artist_name")
}

data class StageState(
    val stageId: String? = null,
    val stageName: String? = null,
    val artistName: String? = null,
    val isGpsAvailable: Boolean = false,
    val isFestivalActive: Boolean = false,
    val lastUpdateMillis: Long = 0L,
    val setProgressPercent: Int = 0,
    val minsToSetEnd: Int = 0,
    val nextArtistName: String? = null
)
