package dev.girg.qwatch.presentation

import android.content.Context
import dev.girg.qwatch.data.parseFavouriteId
import dev.girg.qwatch.resolver.parseTimetable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class TimetableEntry(
    val artist: String,
    val startTime: String,
    val endTime: String,
    val isNow: Boolean,
    val progress: Int,
    val minsLeft: Int
)

data class FavouriteSet(
    val id: String,
    val location: String,
    val stage: StageInfo?,
    val artist: String,
    val startTime: String,
    val endTime: String,
    val startEpochMillis: Long,
    val isNow: Boolean,
    val isPast: Boolean,
    val progress: Int,
    val minsLeft: Int,
    val minsUntilStart: Int
)

data class NowAndNext(
    val nowArtist: String?,
    val nowStart: String?,
    val nowEnd: String?,
    val nowProgress: Int,
    val nowMinsLeft: Int,
    val nextArtist: String?,
    val nextStart: String?
)

class TimetableRepository(context: Context) {
    private val timetable = run {
        val json = context.assets.open("timetable.json").bufferedReader().readText()
        parseTimetable(json)
    }

    private val zoneId = ZoneId.of(timetable.timezone)
    private val eventFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Events before 06:00 belong to the previous calendar day (festival runs past midnight)
    private fun festivalDate(now: ZonedDateTime): LocalDate {
        val local = now.withZoneSameInstant(zoneId)
        return if (local.hour < 6) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun hasEventsToday(locationName: String, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val location = timetable.locations.find { it.name == locationName } ?: return false
        val date = festivalDate(now)
        return location.events.any { event ->
            LocalDateTime.parse(event.start, eventFmt).toLocalDate() == date
        }
    }

    fun getCurrentArtist(locationName: String, now: ZonedDateTime = ZonedDateTime.now()): String? {
        val location = timetable.locations.find { it.name == locationName } ?: return null
        val localNow = now.withZoneSameInstant(zoneId)
        for (event in location.events) {
            val start = LocalDateTime.parse(event.start, eventFmt).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFmt).atZone(zoneId)
            if (!localNow.isBefore(start) && localNow.isBefore(end)) return event.name
        }
        return null
    }

    fun getNowAndNext(locationName: String, now: ZonedDateTime = ZonedDateTime.now()): NowAndNext {
        val location = timetable.locations.find { it.name == locationName }
            ?: return NowAndNext(null, null, null, 0, 0, null, null)
        val localNow = now.withZoneSameInstant(zoneId)
        for ((idx, event) in location.events.withIndex()) {
            val start = LocalDateTime.parse(event.start, eventFmt).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFmt).atZone(zoneId)
            if (!localNow.isBefore(start) && localNow.isBefore(end)) {
                val totalMins = ChronoUnit.MINUTES.between(start, end).toInt()
                val elapsedMins = ChronoUnit.MINUTES.between(start, localNow).toInt()
                val progress = if (totalMins > 0) (elapsedMins * 100 / totalMins).coerceIn(0, 100) else 0
                val minsLeft = ChronoUnit.MINUTES.between(localNow, end).toInt().coerceAtLeast(0)
                val next = location.events.getOrNull(idx + 1)
                return NowAndNext(
                    nowArtist = event.name,
                    nowStart = start.format(timeFmt),
                    nowEnd = end.format(timeFmt),
                    nowProgress = progress,
                    nowMinsLeft = minsLeft,
                    nextArtist = next?.name,
                    nextStart = next?.let { LocalDateTime.parse(it.start, eventFmt).atZone(zoneId).format(timeFmt) }
                )
            }
        }
        return NowAndNext(null, null, null, 0, 0, null, null)
    }

    /**
     * Resolves favourite ids (location + raw start) into displayable sets, sorted by start time.
     * Multiple favourites that collide on the same time are all returned. Past favourites (already
     * ended) are excluded — they are no longer "next".
     */
    fun getFavouriteSets(ids: Set<String>, now: ZonedDateTime = ZonedDateTime.now()): List<FavouriteSet> {
        val localNow = now.withZoneSameInstant(zoneId)
        val result = mutableListOf<FavouriteSet>()
        for (id in ids) {
            val (location, rawStart) = parseFavouriteId(id) ?: continue
            val loc = timetable.locations.find { it.name == location } ?: continue
            val event = loc.events.find { it.start == rawStart } ?: continue
            val start = LocalDateTime.parse(event.start, eventFmt).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFmt).atZone(zoneId)
            val isPast = !localNow.isBefore(end)
            if (isPast) continue
            val isNow = !localNow.isBefore(start)
            val totalMins = ChronoUnit.MINUTES.between(start, end).toInt()
            val progress = if (isNow && totalMins > 0) {
                (ChronoUnit.MINUTES.between(start, localNow).toInt() * 100 / totalMins).coerceIn(0, 100)
            } else 0
            val minsLeft = if (isNow) ChronoUnit.MINUTES.between(localNow, end).toInt().coerceAtLeast(0) else 0
            val minsUntilStart = if (!isNow) ChronoUnit.MINUTES.between(localNow, start).toInt().coerceAtLeast(0) else 0
            result.add(
                FavouriteSet(
                    id = id,
                    location = location,
                    stage = stageForLocation(location),
                    artist = event.name,
                    startTime = start.format(timeFmt),
                    endTime = end.format(timeFmt),
                    startEpochMillis = start.toInstant().toEpochMilli(),
                    isNow = isNow,
                    isPast = false,
                    progress = progress,
                    minsLeft = minsLeft,
                    minsUntilStart = minsUntilStart
                )
            )
        }
        return result.sortedBy { it.startEpochMillis }
    }

    /** The single favourite the watch face should surface: now-playing wins, else the soonest upcoming. */
    fun getNextFavourite(ids: Set<String>, now: ZonedDateTime = ZonedDateTime.now()): FavouriteSet? =
        getFavouriteSets(ids, now).firstOrNull()

    fun getUpcomingEntries(locationName: String, now: ZonedDateTime = ZonedDateTime.now()): List<TimetableEntry> {
        val location = timetable.locations.find { it.name == locationName } ?: return emptyList()
        val localNow = now.withZoneSameInstant(zoneId)
        val date = festivalDate(now)
        val result = mutableListOf<TimetableEntry>()
        for (event in location.events) {
            val start = LocalDateTime.parse(event.start, eventFmt).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFmt).atZone(zoneId)
            if (start.toLocalDate() != date) continue
            if (localNow.isBefore(end)) {
                val isNow = !localNow.isBefore(start)
                val progress: Int
                val minsLeft: Int
                if (isNow) {
                    val totalMins = ChronoUnit.MINUTES.between(start, end).toInt()
                    val elapsedMins = ChronoUnit.MINUTES.between(start, localNow).toInt()
                    progress = if (totalMins > 0) (elapsedMins * 100 / totalMins).coerceIn(0, 100) else 0
                    minsLeft = ChronoUnit.MINUTES.between(localNow, end).toInt().coerceAtLeast(0)
                } else {
                    progress = 0
                    minsLeft = 0
                }
                result.add(TimetableEntry(
                    artist = event.name,
                    startTime = start.format(timeFmt),
                    endTime = end.format(timeFmt),
                    isNow = isNow,
                    progress = progress,
                    minsLeft = minsLeft
                ))
            }
        }
        return result
    }
}
