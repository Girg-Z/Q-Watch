package dev.girg.qwatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.stageDataStore: DataStore<Preferences> by preferencesDataStore(name = "stage_state")

suspend fun Context.writeStageState(state: StageState) {
    stageDataStore.edit { prefs ->
        state.stageId?.let { prefs[StageStateKeys.STAGE_ID] = it }
            ?: prefs.remove(StageStateKeys.STAGE_ID)
        state.stageName?.let { prefs[StageStateKeys.STAGE_NAME] = it }
            ?: prefs.remove(StageStateKeys.STAGE_NAME)
        state.artistName?.let { prefs[StageStateKeys.ARTIST_NAME] = it }
            ?: prefs.remove(StageStateKeys.ARTIST_NAME)
        prefs[StageStateKeys.IS_GPS_AVAILABLE] = state.isGpsAvailable
        prefs[StageStateKeys.IS_FESTIVAL_ACTIVE] = state.isFestivalActive
        prefs[StageStateKeys.LAST_UPDATE_MILLIS] = state.lastUpdateMillis
    }
}

fun Context.readStageStateFlow(): Flow<StageState> = stageDataStore.data.map { prefs ->
    StageState(
        stageId = prefs[StageStateKeys.STAGE_ID],
        stageName = prefs[StageStateKeys.STAGE_NAME],
        artistName = prefs[StageStateKeys.ARTIST_NAME],
        isGpsAvailable = prefs[StageStateKeys.IS_GPS_AVAILABLE] ?: false,
        isFestivalActive = prefs[StageStateKeys.IS_FESTIVAL_ACTIVE] ?: false,
        lastUpdateMillis = prefs[StageStateKeys.LAST_UPDATE_MILLIS] ?: 0L
    )
}
