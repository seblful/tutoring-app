package com.seblful.klas.domain.billing

import com.seblful.klas.domain.time.WallClock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/**
 * Which instant a payment entered for a given **day** is stored at.
 *
 * The tutor answers with a date, because that is what they know; the database stores an instant, and
 * which hour it picks is visible, because the payment list is ordered by instant. Nothing named this
 * module until now — it had no test of any kind, on the path that records money.
 */
class PaymentDateTest {

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val afternoon: Instant = Instant.parse("2026-05-17T11:30:00Z") // 14:30 in Minsk
    private val clock: WallClock = WallClock.fixed(afternoon, zone)

    @Test
    fun `today is read off the injected clock, in its zone`() {
        assertEquals(LocalDate(2026, 5, 17), PaymentDate.today(clock))
    }

    /**
     * So several payments entered in one sitting keep the order they were entered in. A row of identical
     * midnights would shuffle, and the payment list is ordered by instant.
     */
    @Test
    fun `a payment made today keeps the current time`() {
        assertEquals(afternoon, PaymentDate.instantFor(LocalDate(2026, 5, 17), clock))
    }

    /** There is no hour to know, and inventing one would be a precision the tutor never supplied. */
    @Test
    fun `a payment made on another day lands at the start of it`() {
        val tuesday = LocalDate(2026, 5, 12)

        assertEquals(
            tuesday.atStartOfDayIn(zone),
            PaymentDate.instantFor(tuesday, clock),
        )
    }

    /** Correcting a payment forward is allowed; the rule is about the day, not about its direction. */
    @Test
    fun `a future day lands at the start of it too`() {
        val nextWeek = LocalDate(2026, 5, 24)

        assertEquals(
            nextWeek.atStartOfDayIn(zone),
            PaymentDate.instantFor(nextWeek, clock),
        )
    }

    /**
     * "Today" is the clock's zone, not UTC's. At 14:30 in Minsk the UTC date is still the 17th, but an
     * hour before midnight local it would not be — and the two halves of this decision have to agree
     * about which day it is or a payment entered as today loses its place.
     */
    @Test
    fun `the day is the clock's own, not UTC's`() {
        val lateEvening = WallClock.fixed(Instant.parse("2026-05-17T22:30:00Z"), zone) // 01:30 on the 18th
        val local = PaymentDate.today(lateEvening)

        assertEquals(LocalDate(2026, 5, 18), local)
        assertEquals(
            "and the same day is therefore the one that keeps the current time",
            Instant.parse("2026-05-17T22:30:00Z"),
            PaymentDate.instantFor(local, lateEvening),
        )
    }
}
