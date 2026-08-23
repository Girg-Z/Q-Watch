package dev.girg.qwatch.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FestivalScheduleTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun schedule(vararg events: TimetableEvent): FestivalSchedule =
        FestivalSchedule(
            TimetableData(timezone = "Europe/Amsterdam", locations = listOf(TimetableLocation("RED", events.toList())))
        )

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test
    fun `open mid-set`() {
        val s = schedule(TimetableEvent("A", "2026-06-06 14:00", "2026-06-06 15:00"))
        assertTrue(s.isOpen(at(2026, 6, 6, 14, 30), preWarmMinutes = 0))
    }

    @Test
    fun `closed between days`() {
        val s = schedule(
            TimetableEvent("A", "2026-06-06 13:00", "2026-06-07 02:00"),
            TimetableEvent("B", "2026-06-07 13:00", "2026-06-08 02:00")
        )
        // 04:00 on the 7th is after the first window's 02:00 end and before the next 13:00 open.
        assertFalse(s.isOpen(at(2026, 6, 7, 4, 0), preWarmMinutes = 0))
    }

    @Test
    fun `past-midnight end keeps the window open`() {
        val s = schedule(TimetableEvent("A", "2026-06-06 13:00", "2026-06-07 02:00"))
        assertTrue(s.isOpen(at(2026, 6, 7, 1, 30), preWarmMinutes = 0))
    }

    @Test
    fun `adjacent events merge into one window`() {
        val s = schedule(
            TimetableEvent("A", "2026-06-06 14:00", "2026-06-06 15:00"),
            TimetableEvent("B", "2026-06-06 15:00", "2026-06-06 16:00")
        )
        // Inside the merged 14:00-16:00 window at the seam.
        assertTrue(s.isOpen(at(2026, 6, 6, 15, 0), preWarmMinutes = 0))
    }

    @Test
    fun `pre-warm opens before the first set`() {
        val s = schedule(TimetableEvent("A", "2026-06-06 14:00", "2026-06-06 15:00"))
        assertFalse(s.isOpen(at(2026, 6, 6, 13, 50), preWarmMinutes = 5))
        assertTrue(s.isOpen(at(2026, 6, 6, 13, 56), preWarmMinutes = 5))
    }

    @Test
    fun `nextOpen returns the upcoming window start`() {
        val s = schedule(
            TimetableEvent("A", "2026-06-06 13:00", "2026-06-07 02:00"),
            TimetableEvent("B", "2026-06-07 13:00", "2026-06-08 02:00")
        )
        assertEquals(at(2026, 6, 7, 13, 0), s.nextOpen(at(2026, 6, 7, 4, 0)))
    }

    @Test
    fun `nextOpen is null when no future window`() {
        val s = schedule(TimetableEvent("A", "2026-06-06 13:00", "2026-06-07 02:00"))
        assertNull(s.nextOpen(at(2026, 6, 8, 12, 0)))
    }
}
