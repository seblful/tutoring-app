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

/** oday by this clock — **re-emitted at every local midnight**, not read once. */
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
