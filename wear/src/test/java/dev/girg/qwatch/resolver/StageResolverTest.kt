package dev.girg.qwatch.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StageResolverTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    // A large square polygon containing lat=51.0 lng=5.0
    private val polygon = listOf(
        Pair(4.9, 50.9), Pair(5.1, 50.9),
        Pair(5.1, 51.1), Pair(4.9, 51.1),
        Pair(4.9, 50.9)
    )

    private fun resolver(events: List<TimetableEvent>): StageResolver {
        val timetable = TimetableData(
            timezone = "Europe/Amsterdam",
            locations = listOf(TimetableLocation("TEST", events))
        )
        val days = listOf(
            FestivalDay(
                date = "2025-06-07",
                stages = listOf(Stage("test", "TEST", polygon))
            )
        )
        return StageResolver(timetable, days)
    }

    @Test
    fun `progress is 50 when halfway through a 60-minute set`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00")
        )
        val now = ZonedDateTime.of(2025, 6, 7, 14, 30, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertEquals(50, result.setProgressPercent)
    }

    @Test
    fun `minsToSetEnd is 30 when halfway through a 60-minute set`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00")
        )
        val now = ZonedDateTime.of(2025, 6, 7, 14, 30, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertEquals(30, result.minsToSetEnd)
    }

    @Test
    fun `nextArtistName is populated when a following event exists`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00"),
            TimetableEvent("Artist B", "2025-06-07 15:00", "2025-06-07 16:00")
        )
        val now = ZonedDateTime.of(2025, 6, 7, 14, 30, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertEquals("Artist B", result.nextArtistName)
    }

    @Test
    fun `nextArtistName is null when no following event exists`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00")
        )
        val now = ZonedDateTime.of(2025, 6, 7, 14, 30, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertNull(result.nextArtistName)
    }

    @Test
    fun `progress and minsToSetEnd are 0 when no active set`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00")
        )
        // now is before the event
        val now = ZonedDateTime.of(2025, 6, 7, 13, 0, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertEquals(0, result.setProgressPercent)
        assertEquals(0, result.minsToSetEnd)
        assertNull(result.artistName)
    }

    @Test
    fun `progress is 0 and minsToSetEnd is 60 at set start`() {
        val events = listOf(
            TimetableEvent("Artist A", "2025-06-07 14:00", "2025-06-07 15:00")
        )
        val now = ZonedDateTime.of(2025, 6, 7, 14, 0, 0, 0, zone)
        val result = resolver(events).resolve(51.0, 5.0, now) as ResolveResult.AtStage

        assertEquals(0, result.setProgressPercent)
        assertEquals(60, result.minsToSetEnd)
    }
}
