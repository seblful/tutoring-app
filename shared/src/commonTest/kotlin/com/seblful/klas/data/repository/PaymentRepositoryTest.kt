package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentRepositoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val today: LocalDate = LocalDate(2026, 5, 17)
    private val afternoon: Instant = today.atTime(14, 30).toInstant(zone)
    private val clock = MutableClock(afternoon, zone)

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
        val tuesday = LocalDate(2026, 5, 12)

        payments.record(sid, Money.ofMajor(40), tuesday)

        assertEquals(
            tuesday.atStartOfDayIn(zone),
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

        assertEquals(afternoon.plus((3_600).seconds), payments.observeForStudent(sid).first().single().date)
    }

    @Test
    fun `correcting a payment keeps its id and re-derives its instant`() = runTest {
        val sid = student()
        payments.record(sid, Money.ofMajor(40), today)
        val original = payments.observeForStudent(sid).first().single()
        val tuesday = LocalDate(2026, 5, 12)

        payments.correct(original, Money.ofMajor(45), tuesday)

        val corrected = payments.observeForStudent(sid).first().single()
        assertEquals(original.id, corrected.id, "the same money, not a new row")
        assertEquals(Money.ofMajor(45), corrected.amount)
        assertEquals(tuesday.atStartOfDayIn(zone), corrected.date)
    }

    /**
     * The one write that does not derive an instant. An undo restores a fact rather than recording one:
     * re-deriving would move a payment made last Tuesday to the moment the tutor tapped «Вернуть».
     */
    @Test
    fun `restoring puts a payment back at its own instant - whatever the clock says`() = runTest {
        val sid = student()
        val lastMonth = Instant.parse("2026-04-02T08:15:00Z")

        payments.restore(Payment(studentId = sid, date = lastMonth, amount = Money.ofMajor(40)))

        assertEquals(lastMonth, payments.observeForStudent(sid).first().single().date)
    }

    @Test
    fun `undoing a correction puts the payment back as it was`() = runTest {
        val sid = student()
        payments.record(sid, Money.ofMajor(40), today)
        val original = payments.observeForStudent(sid).first().single()

        val corrected = payments.correct(original, Money.ofMajor(45), LocalDate(2026, 5, 12))

        assertTrue(payments.undoCorrection(original, corrected))
        assertEquals(original, payments.observeForStudent(sid).first().single())
    }

    @Test
    fun `an undo never overwrites a later correction or revives a deleted payment`() = runTest {
        val sid = student()
        payments.record(sid, Money.ofMajor(40), today)
        val original = payments.observeForStudent(sid).first().single()
        val corrected = payments.correct(original, Money.ofMajor(45), today)
        val later = payments.correct(corrected, Money.ofMajor(50), today)

        assertFalse(payments.undoCorrection(original, corrected))
        assertEquals(later, payments.observeForStudent(sid).first().single())

        payments.delete(later)
        assertFalse(payments.undoCorrection(corrected, later))
        assertTrue(payments.observeForStudent(sid).first().isEmpty())
    }
}
