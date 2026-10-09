package com.seblful.klas.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The instants one local day covers: from its first moment up to, **not including**, the next day's —
 * half-open like every window the class queries take (`ClassRepository.getBetween`).
 *
 * Midnight to midnight by the zone's rules rather than 24 hours from the start, because a day the clocks
 * change on is 23 or 25 hours long, and a window a fixed length would cut off its last lesson or reach into
 * the next morning's. The two lesson-listing digests and the schedule's week and month queries each
 * spelled this out by hand; stated once, they cannot come to disagree about where a day ends.
 */
data class DayBounds(val start: Instant, val end: Instant) {

    companion object {
        fun of(day: LocalDate, zone: ZoneId): DayBounds = of(day, day.plusDays(1), zone)

        /** The days from [first] up to, not including, [endExclusive] — a week, or a month's grid. */
        fun of(first: LocalDate, endExclusive: LocalDate, zone: ZoneId): DayBounds =
            DayBounds(first.atStartOfDay(zone).toInstant(), endExclusive.atStartOfDay(zone).toInstant())
    }
}
