package dev.girg.qwatch.service

import android.content.ComponentName
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dev.girg.qwatch.complication.NextFavouriteComplicationService
import dev.girg.qwatch.data.writeFavouriteIds
import kotlinx.coroutines.runBlocking

/**
 * Receives the favourites set pushed from the phone over the Wearable Data Layer and persists the
 * ids locally so the complication and the Favourites screen can resolve them against the bundled
 * timetable.
 *
 * Path: "/favourites", key "ids" → StringArrayList of "location|start" favourite ids.
 */
class FavouritesSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != PATH_FAVOURITES) continue

            val dataMap = DataMapItem.fromDataItem(item).dataMap
            val ids = dataMap.getStringArrayList(KEY_IDS)?.toSet() ?: emptySet()
            Log.d(TAG, "Received ${ids.size} favourites from phone")

            runBlocking { applicationContext.writeFavouriteIds(ids) }

            ComplicationDataSourceUpdateRequester
                .create(
                    applicationContext,
                    ComponentName(applicationContext, NextFavouriteComplicationService::class.java)
                )
                .requestUpdateAll()
        }
    }

    companion object {
        private const val TAG = "FavouritesSync"
        const val PATH_FAVOURITES = "/favourites"
        const val KEY_IDS = "ids"
    }
}
