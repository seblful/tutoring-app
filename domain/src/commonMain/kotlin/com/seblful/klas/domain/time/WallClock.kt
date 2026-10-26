package com.seblful.klas.domain.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The wall clock, and the zone it is read in.
 *
 * `java.time.Clock` was both, and the code leans on that: "today" is a date, and a date only exists in a
 * zone. `kotlin.time.Clock` is only the instant, so the zone travels beside it here. Injected rather than
 * read from the system for the reason `di/TimeModule` gives — a test has to be able to move the time and
 * name the zone, and a midnight rollover is only assertable against a clock the test controls.
 */
interface WallClock {

    /** The current instant. */
    fun now(): Instant

    /** The zone a date or a time of day is read in — the device's, for the app. */
    val zone: TimeZone

    companion object {

        /** A clock stopped at [now], read in [zone] — for fixtures and tests. */
        fun fixed(now: Instant, zone: TimeZone): WallClock = FixedClock(now, zone)

        /** The system's clock, read in [zone] — the device's own zone unless one is named. */
        fun system(zone: TimeZone = TimeZone.currentSystemDefault()): WallClock = SystemClock(zone)
    }
}

/** Today's date by this clock. */
fun WallClock.today(): LocalDate = now().toLocalDateTime(zone).date

private data class FixedClock(private val instant: Instant, override val zone: TimeZone) : WallClock {
    override fun now(): Instant = instant
}

private data class SystemClock(override val zone: TimeZone) : WallClock {
    override fun now(): Instant = Clock.System.now()
}
