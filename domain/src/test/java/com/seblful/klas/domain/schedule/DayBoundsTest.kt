package com.seblful.klas.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where a local day starts and stops — what the digests read a day's lessons over. */
class DayBoundsTest {

    @Test
    fun `a day runs from its midnight to the next one, the next excluded`() {
        val bounds = DayBounds.of(LocalDate.of(2026, 6, 15), ZoneId.of("Europe/Minsk"))

        assertEquals(Instant.parse("2026-06-14T21:00:00Z"), bounds.start)
        assertEquals(Instant.parse("2026-06-15T21:00:00Z"), bounds.end)
    }

    /** The reason this is midnight to midnight and not a start plus 24 hours. */
    @Test
    fun `a day the clocks go forward on is 23 hours long`() {
        val bounds = DayBounds.of(LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"))

        assertEquals(Duration.ofHours(23), Duration.between(bounds.start, bounds.end))
    }

    @Test
    fun `a run of days ends at the midnight that opens the excluded one`() {
        val zone = ZoneId.of("Europe/Berlin")
        val bounds = DayBounds.of(LocalDate.of(2026, 3, 23), LocalDate.of(2026, 3, 30), zone)

        assertEquals(DayBounds.of(LocalDate.of(2026, 3, 23), zone).start, bounds.start)
        assertEquals(DayBounds.of(LocalDate.of(2026, 3, 29), zone).end, bounds.end)
        assertEquals(Duration.ofDays(7).minusHours(1), Duration.between(bounds.start, bounds.end))
    }
}
