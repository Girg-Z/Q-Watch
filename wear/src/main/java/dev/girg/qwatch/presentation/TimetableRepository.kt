package dev.girg.qwatch.presentation

import android.content.Context
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
