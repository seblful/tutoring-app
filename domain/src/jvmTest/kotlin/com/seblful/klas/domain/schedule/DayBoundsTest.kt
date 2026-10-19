package com.seblful.klas.domain.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Where a local day starts and stops — what the digests read a day's lessons over. */
class DayBoundsTest {

    @Test
    fun `a day runs from its midnight to the next one, the next excluded`() {
        val bounds = DayBounds.of(LocalDate(2026, 6, 15), TimeZone.of("Europe/Minsk"))

        assertEquals(Instant.parse("2026-06-14T21:00:00Z"), bounds.start)
        assertEquals(Instant.parse("2026-06-15T21:00:00Z"), bounds.end)
    }

    /** The reason this is midnight to midnight and not a start plus 24 hours. */
    @Test
    fun `a day the clocks go forward on is 23 hours long`() {
        val bounds = DayBounds.of(LocalDate(2026, 3, 29), TimeZone.of("Europe/Berlin"))

        assertEquals(23.hours, (bounds.end - bounds.start))
    }

    @Test
    fun `a run of days ends at the midnight that opens the excluded one`() {
        val zone = TimeZone.of("Europe/Berlin")
        val bounds = DayBounds.of(LocalDate(2026, 3, 23), LocalDate(2026, 3, 30), zone)

        assertEquals(DayBounds.of(LocalDate(2026, 3, 23), zone).start, bounds.start)
        assertEquals(DayBounds.of(LocalDate(2026, 3, 29), zone).end, bounds.end)
        assertEquals(7.days - 1.hours, bounds.end - bounds.start)
    }
}
