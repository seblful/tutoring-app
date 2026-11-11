package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The details card's −/+: how far a press goes, and where it stops. */
class StepsTest {

    @Test
    fun `length moves a quarter of an hour — between a quarter and a day`() {
        assertEquals(75, Steps.length(60, longer = true))
        assertEquals(45, Steps.length(60, longer = false))
        assertNull(Steps.length(15, longer = false))
        assertEquals(15, Steps.length(20, longer = false))
        assertEquals(24 * 60 - 1, Steps.length(23 * 60 + 50, longer = true))
        assertNull(Steps.length(24 * 60 - 1, longer = true))
    }

    @Test
    fun `lessons go from one — which is once — to a year of them`() {
        assertEquals(2, Steps.lessons(1, more = true))
        assertNull(Steps.lessons(1, more = false))
        assertNull(Steps.lessons(Recurrence.MAX_COUNT, more = true))
    }

    @Test
    fun `a rate steps by an amount that suits its size`() {
        assertEquals(Money.ofMajor(5), Steps.rateStep(Money.ofMajor(40)))
        assertEquals(Money.ofMajor(100), Steps.rateStep(Money.ofMajor(1500)))
        assertEquals(Money.ofMajor(1), Steps.rateStep(Money.ofMajor(9)))
        assertEquals(Money.ofMajor(1), Steps.rateStep(Money.ZERO))
    }

    @Test
    fun `a rate never goes below zero or past the ceiling`() {
        assertEquals(Money.ofMajor(45), Steps.rate(Money.ofMajor(40), higher = true))
        assertEquals(Money.ofMajor(35), Steps.rate(Money.ofMajor(40), higher = false))
        assertEquals(Money.ZERO, Steps.rate(Money(50L), higher = false))
        assertNull(Steps.rate(Money.ZERO, higher = false))
        assertNull(Steps.rate(Money.MAX, higher = true))
    }

    @Test
    fun `one lesson is a booking that does not repeat`() {
        val weekly = ClassForm(repeat = Repeat.FORTNIGHTLY, repeatCount = 8)

        assertEquals(8, weekly.lessons)
        assertEquals(Repeat.NONE, weekly.withLessons(1).repeat)
        assertEquals(1, weekly.withLessons(1).lessons)
    }

    @Test
    fun `more than one lesson keeps the frequency — weekly when there was none`() {
        assertEquals(Repeat.WEEKLY, ClassForm().withLessons(2).repeat)
        assertEquals(2, ClassForm().withLessons(2).repeatCount)
        assertEquals(Repeat.FORTNIGHTLY, ClassForm(repeat = Repeat.FORTNIGHTLY).withLessons(5).repeat)
    }
}
