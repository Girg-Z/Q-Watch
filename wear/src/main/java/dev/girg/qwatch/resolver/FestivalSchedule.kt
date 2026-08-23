package dev.girg.qwatch.resolver

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

/**
 * Derives the festival's "open" periods from the timetable so the location loop can stay fully
 * idle outside event hours (e.g. overnight while camping on-site).
 *
 * Every event [start, end] interval across all locations is collected, sorted and merged into
 * contiguous windows. This naturally absorbs sets that run past midnight (13:00 -> 02:00 next day)
 * without any hardcoded daily schedule.
 */
class FestivalSchedule(timetable: TimetableData) {

    private val zoneId: ZoneId = ZoneId.of(timetable.timezone)
    private val windows: List<Window> = buildWindows(timetable)

    private data class Window(val start: ZonedDateTime, val end: ZonedDateTime)

    private fun buildWindows(timetable: TimetableData): List<Window> {
        val intervals = timetable.locations
            .flatMap { it.events }
            .mapNotNull { event ->
                runCatching {
                    val start = LocalDateTime.parse(event.start, FORMATTER).atZone(zoneId)
                    val end = LocalDateTime.parse(event.end, FORMATTER).atZone(zoneId)
                    Window(start, end)
                }.getOrNull()
            }
            .sortedBy { it.start }

        if (intervals.isEmpty()) return emptyList()

        val merged = mutableListOf(intervals.first())
        for (next in intervals.drop(1)) {
            val last = merged.last()
            // Overlapping or back-to-back -> extend the current window.
            if (!next.start.isAfter(last.end)) {
                if (next.end.isAfter(last.end)) merged[merged.lastIndex] = last.copy(end = next.end)
            } else {
                merged.add(next)
            }
        }
        return merged
    }

    /**
     * Open if [now] sits inside a window, with the front edge pulled back by [preWarmMinutes] so the
     * loop spins up shortly before the first set of the day.
     */
    fun isOpen(now: ZonedDateTime, preWarmMinutes: Long = 5): Boolean =
        windows.any { w -> !now.isBefore(w.start.minusMinutes(preWarmMinutes)) && now.isBefore(w.end) }

    /** Start of the next window that has not yet begun, or null if there is none. */
    fun nextOpen(now: ZonedDateTime): ZonedDateTime? =
        windows.firstOrNull { it.start.isAfter(now) }?.start

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
