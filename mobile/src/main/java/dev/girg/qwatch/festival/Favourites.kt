package dev.girg.qwatch.festival

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// Keep these in sync with wear's dev.girg.qwatch.data.Favourites (no shared module yet).
private const val FAVOURITE_ID_SEPARATOR = "|"

fun favouriteId(location: String, rawStart: String): String =
    location + FAVOURITE_ID_SEPARATOR + rawStart

private object FavouriteKeys {
    val FAVOURITE_IDS = stringSetPreferencesKey("favourite_ids")
}

private val Context.favouritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favourites")

fun Context.readFavouriteIdsFlow(): Flow<Set<String>> =
    favouritesDataStore.data.map { it[FavouriteKeys.FAVOURITE_IDS] ?: emptySet() }

/** Toggle a favourite on/off, persist, and push the new set to the watch. */
suspend fun Context.toggleFavourite(id: String) {
    var updated: Set<String> = emptySet()
    favouritesDataStore.edit { prefs ->
        val current = prefs[FavouriteKeys.FAVOURITE_IDS] ?: emptySet()
        updated = if (id in current) current - id else current + id
        prefs[FavouriteKeys.FAVOURITE_IDS] = updated
    }
    syncFavouritesToWatch(updated)
}

/** Sends the favourites set to the watch over the Wearable Data Layer (path "/favourites"). */
suspend fun Context.syncFavouritesToWatch(ids: Set<String>) {
    try {
        val request = PutDataMapRequest.create(PATH_FAVOURITES).apply {
            dataMap.putStringArrayList(KEY_IDS, ArrayList(ids))
            // Force a distinct payload each push so identical sets still propagate.
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request).await()
        Log.d(TAG, "Synced ${ids.size} favourites to watch")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to sync favourites to watch", e)
    }
}

private const val TAG = "Favourites"
const val PATH_FAVOURITES = "/favourites"
const val KEY_IDS = "ids"
