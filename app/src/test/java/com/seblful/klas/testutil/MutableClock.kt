package com.seblful.klas.testutil

import com.seblful.klas.domain.time.WallClock
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A clock a test moves by hand, for the one piece of code whose job is to watch the date change.
 *
 * A test scheduler's `advanceTimeBy` moves *virtual* coroutine time, which the system clock knows
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
    override val zone: TimeZone = TimeZone.currentSystemDefault(),
) : WallClock {

    override fun now(): Instant = instant

    fun advanceBy(millis: Long) {
        instant = instant.plus(millis.milliseconds)
    }
}
