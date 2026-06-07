package dev.girg.qwatch.resolver

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime

sealed class ResolveResult {
    data class AtStage(
        val stageId: String,
        val stageName: String,
        val artistName: String?,
        val nextArtistName: String?,
        val setProgressPercent: Int,
        val minsToSetEnd: Int
    ) : ResolveResult()
    object BetweenStages : ResolveResult()
    object FestivalInactive : ResolveResult()
}

class StageResolver(
    private val timetable: TimetableData,
    private val days: List<FestivalDay>
) {
    private val eventFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun resolve(lat: Double, lng: Double, now: ZonedDateTime): ResolveResult {
        val zoneId = ZoneId.of(timetable.timezone)
        val localDate = now.withZoneSameInstant(zoneId).toLocalDate()
        val dateStr = localDate.toString()

        val day = days.find { it.date == dateStr } ?: return ResolveResult.FestivalInactive

        for (stage in day.stages) {
            if (isInsidePolygon(lat, lng, stage.polygon)) {
                val location = timetable.locations.find { it.name == stage.displayName }
                val info = location?.let { findCurrentSetInfo(it, now, zoneId) }
                return ResolveResult.AtStage(
                    stageId = stage.id,
                    stageName = stage.displayName,
                    artistName = info?.artistName,
                    nextArtistName = info?.nextArtistName,
                    setProgressPercent = info?.setProgressPercent ?: 0,
                    minsToSetEnd = info?.minsToSetEnd ?: 0
                )
            }
        }
        return ResolveResult.BetweenStages
    }

    private fun isInsidePolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        // polygon[i] = (longitude, latitude) — GeoJSON order
        // PNPOLY ray-casting
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val lngI = polygon[i].first
            val latI = polygon[i].second
            val lngJ = polygon[j].first
            val latJ = polygon[j].second
            if ((latI > lat) != (latJ > lat) &&
                lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    internal data class CurrentSetInfo(
        val artistName: String?,
        val nextArtistName: String?,
        val setProgressPercent: Int,
        val minsToSetEnd: Int
    )

    internal fun findCurrentSetInfo(
        location: TimetableLocation,
        now: ZonedDateTime,
        zoneId: ZoneId
    ): CurrentSetInfo {
        for ((index, event) in location.events.withIndex()) {
            val start = LocalDateTime.parse(event.start, eventFormatter).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFormatter).atZone(zoneId)
            if (!now.isBefore(start) && now.isBefore(end)) {
                val totalMins = ChronoUnit.MINUTES.between(start, end).toInt()
                val elapsedMins = ChronoUnit.MINUTES.between(start, now).toInt()
                val progress = if (totalMins > 0) (elapsedMins * 100 / totalMins).coerceIn(0, 100) else 0
                val minsLeft = ChronoUnit.MINUTES.between(now, end).toInt().coerceAtLeast(0)
                val nextArtist = location.events.getOrNull(index + 1)?.name
                return CurrentSetInfo(event.name, nextArtist, progress, minsLeft)
            }
        }
        return CurrentSetInfo(null, null, 0, 0)
    }
}
