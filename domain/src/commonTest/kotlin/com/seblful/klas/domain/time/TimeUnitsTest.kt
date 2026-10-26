package com.seblful.klas.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TimeUnitsTest {

    @Test
    fun `the units agree with the duration library`() {
        assertEquals(1.days, MINUTES_PER_DAY.minutes)
        assertEquals(1.hours, MINUTES_PER_HOUR.minutes)
        assertEquals(1.days.inWholeSeconds, SECONDS_PER_DAY.toLong())
        assertEquals(1.minutes.inWholeSeconds, SECONDS_PER_MINUTE.toLong())
        assertEquals(1.minutes.inWholeMilliseconds, MILLIS_PER_MINUTE)
    }
}
