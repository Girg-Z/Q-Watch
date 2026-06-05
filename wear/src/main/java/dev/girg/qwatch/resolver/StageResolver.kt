package dev.girg.qwatch.resolver

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class ResolveResult {
    data class AtStage(
        val stageId: String,
        val stageName: String,
        val artistName: String?
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
        val dateStr = localDate.toString() // "yyyy-MM-dd"

        val day = days.find { it.date == dateStr } ?: return ResolveResult.FestivalInactive

        for (stage in day.stages) {
            if (isInsidePolygon(lat, lng, stage.polygon)) {
                val location = timetable.locations.find { it.name == stage.displayName }
                val artist = location?.let { findCurrentArtist(it, now, zoneId) }
                return ResolveResult.AtStage(stage.id, stage.displayName, artist)
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

    private fun findCurrentArtist(
        location: TimetableLocation,
        now: ZonedDateTime,
        zoneId: ZoneId
    ): String? {
        for (event in location.events) {
            val start = LocalDateTime.parse(event.start, eventFormatter).atZone(zoneId)
            val end = LocalDateTime.parse(event.end, eventFormatter).atZone(zoneId)
            if (!now.isBefore(start) && now.isBefore(end)) return event.name
        }
        return null
    }
}
