package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * That a payment's instant is derived by the module that writes it.
 *
 * This repository had **no behavioural test at all**: five public methods, five one-line delegates to a
 * `PaymentDao` of the same shape, with the one rule it was documented as owning — a payment's own date —
 * applied by the view model on the way in. The rule is here now, so it can be asserted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaymentRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val today: LocalDate = LocalDate.of(2026, 5, 17)
    private val afternoon: Instant = today.atTime(14, 30).atZone(zone).toInstant()
    private val clock = MutableClock(afternoon, zone)

    @get:Rule
    val env = TestEnv(clock = clock)
    private val payments = env.paymentRepo

    private suspend fun student(): Long = env.fixtures.student()

    @Test
    fun `today is the repository's own clock`() = runTest {
        assertEquals(today, payments.today())
    }

    /** Several payments entered in one sitting have to keep the order they were entered in. */
    @Test
    fun `a payment recorded for today keeps the current time`() = runTest {
        val sid = student()

        payments.record(sid, Money.ofMajor(40), today)

        assertEquals(afternoon, payments.observeForStudent(sid).first().single().date)
    }

    @Test
    fun `a payment recorded for another day lands at the start of it`() = runTest {
        val sid = student()
        val tuesday = LocalDate.of(2026, 5, 12)

        payments.record(sid, Money.ofMajor(40), tuesday)

        assertEquals(
            tuesday.atStartOfDay(zone).toInstant(),
            payments.observeForStudent(sid).first().single().date,
        )
    }

    /**
     * The whole point of moving the rule in here: no caller supplies an instant, so none can supply a
     * wrong one. What reaches the row is a function of the day the tutor named and the injected clock.
     */
    @Test
    fun `moving the clock moves where a payment lands`() = runTest {
        val sid = student()
        clock.advanceBy(60 * 60 * 1000L)

        payments.record(sid, Money.ofMajor(40), today)

        assertEquals(afternoon.plusSeconds(3_600), payments.observeForStudent(sid).first().single().date)
    }

    @Test
    fun `correcting a payment keeps its id and re-derives its instant`() = runTest {
        val sid = student()
        payments.record(sid, Money.ofMajor(40), today)
        val original = payments.observeForStudent(sid).first().single()
        val tuesday = LocalDate.of(2026, 5, 12)

        payments.correct(original, Money.ofMajor(45), tuesday)

        val corrected = payments.observeForStudent(sid).first().single()
        assertEquals("the same money, not a new row", original.id, corrected.id)
        assertEquals(Money.ofMajor(45), corrected.amount)
        assertEquals(tuesday.atStartOfDay(zone).toInstant(), corrected.date)
    }

    /**
     * The one write that does not derive an instant. An undo restores a fact rather than recording one:
     * re-deriving would move a payment made last Tuesday to the moment the tutor tapped «Вернуть».
     */
    @Test
    fun `restoring puts a payment back at its own instant, whatever the clock says`() = runTest {
        val sid = student()
        val lastMonth = Instant.parse("2026-04-02T08:15:00Z")

        payments.restore(Payment(studentId = sid, date = lastMonth, amount = Money.ofMajor(40)))

        assertEquals(lastMonth, payments.observeForStudent(sid).first().single().date)
    }
}
