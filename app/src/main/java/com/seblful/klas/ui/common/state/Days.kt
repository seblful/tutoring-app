package com.seblful.klas.ui.common.state

import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.zones
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Today by this clock — **re-emitted at every local midnight**, not read once.
 *
 * A screen that computes "today" once goes on using it for as long as it stays alive, and the tabs and
 * cards stay alive behind the bar: an app left open overnight — which is exactly what a phone on a desk
 * does — went on marking yesterday as today, and calling yesterday's lesson's homework due today.
 *
 * Sleeping until the next midnight rather than polling: the answer changes once a day and it is knowable
 * when. `delay` is virtual time under a test scheduler, so the rollover is testable without waiting for it.
 *
 * **A test that collects a flow built on this must advance time by a stated amount, never
 * `advanceUntilIdle()`.** A clock has no last emission, so there is always another `delay` on the
 * scheduler and "until idle" never arrives — it hangs rather than failing.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun WallClock.days(): Flow<LocalDate> = zones().flatMapLatest { zone ->
    flow {
        while (true) {
            val today = now().toLocalDateTime(zone).date
            emit(today)
            val midnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            // At least a tick, so a clock already past midnight cannot spin on a zero-length sleep.
            delay((midnight - now()).inWholeMilliseconds.coerceAtLeast(1))
        }
    }
}

/**
 * The wall clock, once a minute, **aligned to the minute** — so whatever is drawn from it moves as the
 * phone's clock changes rather than a stray number of seconds after the screen was opened.
 *
 * Sleeping to the next boundary rather than polling a second at a time, for the reason [days] sleeps to the
 * next midnight: the answer changes on a schedule, and it is knowable when. The same rule for tests holds:
 * advance time by a stated amount, never `advanceUntilIdle()`.
 */
fun WallClock.minutes(): Flow<Instant> = flow {
    while (true) {
        val instant = now()
        emit(instant)
        val nextMinute = instant.plus(SECONDS_PER_MINUTE.seconds)
            .minus(instant.nanosecondsOfSecond.nanoseconds)
            .minus(instant.epochSeconds.mod(SECONDS_PER_MINUTE).seconds)
        // At least a tick: a clock less than a millisecond short of the boundary truncates to a
        // zero-length sleep, and this loop would spin on it.
        delay((nextMinute - instant).inWholeMilliseconds.coerceAtLeast(1))
    }
}

private const val SECONDS_PER_MINUTE = 60L
