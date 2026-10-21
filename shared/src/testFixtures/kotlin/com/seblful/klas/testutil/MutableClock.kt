package com.seblful.klas.testutil

import com.seblful.klas.domain.time.WallClock
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class MutableClock(
    private var instant: Instant,
    override val zone: TimeZone = TimeZone.of("Europe/Minsk"),
) : WallClock {

    override fun now(): Instant = instant

    fun advanceBy(millis: Long) {
        instant = instant.plus(millis.milliseconds)
    }
}

fun testClock(): MutableClock = MutableClock(Instant.parse("2026-06-01T12:00:00Z"))
