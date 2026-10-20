package com.seblful.klas.domain.billing

import com.seblful.klas.domain.time.WallClock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PaymentDateTest {

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val afternoon: Instant = Instant.parse("2026-05-17T11:30:00Z") // 14:30 in Minsk
    private val clock: WallClock = WallClock.fixed(afternoon, zone)

    @Test
    fun `today is read off the injected clock - in its zone`() {
        assertEquals(LocalDate(2026, 5, 17), PaymentDate.today(clock))
    }

    @Test
    fun `a payment made today keeps the current time`() {
        assertEquals(afternoon, PaymentDate.instantFor(LocalDate(2026, 5, 17), clock))
    }

    @Test
    fun `a payment made on another day lands at the start of it`() {
        val tuesday = LocalDate(2026, 5, 12)

        assertEquals(
            tuesday.atStartOfDayIn(zone),
            PaymentDate.instantFor(tuesday, clock),
        )
    }

    @Test
    fun `a future day lands at the start of it too`() {
        val nextWeek = LocalDate(2026, 5, 24)

        assertEquals(
            nextWeek.atStartOfDayIn(zone),
            PaymentDate.instantFor(nextWeek, clock),
        )
    }

    @Test
    fun `the day is the clock's own - not UTC's`() {
        val lateEvening = WallClock.fixed(Instant.parse("2026-05-17T22:30:00Z"), zone) // 01:30 on the 18th
        val local = PaymentDate.today(lateEvening)

        assertEquals(LocalDate(2026, 5, 18), local)
        assertEquals(
            Instant.parse("2026-05-17T22:30:00Z"),
            PaymentDate.instantFor(local, lateEvening),
            "and the same day is therefore the one that keeps the current time",
        )
    }
}
