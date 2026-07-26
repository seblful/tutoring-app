package com.seblful.klas.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A clock a test moves by hand, for the one piece of code whose job is to watch the date change.
 *
 * A test scheduler's `advanceTimeBy` moves *virtual* coroutine time, which `LocalDate.now()` knows
 * nothing about — so a midnight rollover asserted against the system clock can only ever come back as
 * "still today". Moving both together is what makes the boundary an ordinary assertion:
 *
 * ```
 * clock.advanceBy(untilMidnight)   // what the date is read from
 * advanceTimeBy(untilMidnight)     // what the delay is waiting on
 * ```
 *
 * The order matters: set the clock **first**, because advancing virtual time is what runs the
 * continuation that re-reads it.
 */
class MutableClock(
    private var instant: Instant,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : Clock() {

    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    fun advanceBy(millis: Long) {
        instant = instant.plusMillis(millis)
    }
}
