package com.seblful.klas.ui.common.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

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
fun Clock.days(): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now(this@days)
        emit(today)
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        // At least a tick, so a clock already past midnight cannot spin on a zero-length sleep.
        delay(Duration.between(instant(), midnight).toMillis().coerceAtLeast(1))
    }
}
