package dev.girg.qwatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A favourite set is identified by the timetable location (e.g. "RED") plus the event start
 * timestamp (the raw "yyyy-MM-dd HH:mm" string from timetable.json). That pair is unique — a single
 * stage cannot run two sets at the same instant — and is stable across the phone and the watch
 * because both read the same bundled timetable.
 *
 * Two different stages CAN have favourites that start at the same time; those are distinct ids and
 * both are kept.
 */
const val FAVOURITE_ID_SEPARATOR = "|"

fun favouriteId(location: String, start: String): String =
    location + FAVOURITE_ID_SEPARATOR + start

/** Returns (location, start) for a favourite id, or null if malformed. */
fun parseFavouriteId(id: String): Pair<String, String>? {
    val parts = id.split(FAVOURITE_ID_SEPARATOR, limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else null
}

object FavouriteKeys {
    val FAVOURITE_IDS = stringSetPreferencesKey("favourite_ids")
}

val Context.favouritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favourites")

fun Context.readFavouriteIdsFlow(): Flow<Set<String>> =
    favouritesDataStore.data.map { prefs -> prefs[FavouriteKeys.FAVOURITE_IDS] ?: emptySet() }

suspend fun Context.writeFavouriteIds(ids: Set<String>) {
    favouritesDataStore.edit { prefs ->
        prefs[FavouriteKeys.FAVOURITE_IDS] = ids
    }
}
