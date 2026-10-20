package com.seblful.klas.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceTest {

    @Test
    fun `nothing owed either way is settled`() {
        assertEquals(Balance.Settled, Balance.of(Money.ZERO))
    }

    @Test
    fun `a positive balance is what the student owes`() {
        assertEquals(Balance.Owed(Money.ofMajor(240)), Balance.of(Money.ofMajor(240)))
    }

    @Test
    fun `an over-payment is credit, and its amount is positive`() {
        val credit = Balance.of(Money.ofMajor(-50))

        assertEquals(Balance.Credit(Money.ofMajor(50)), credit)
        assertEquals(Money.ofMajor(50), credit.amount, "positive, ready to print")
    }

    @Test
    fun `a settled balance carries a zero to print`() {
        assertEquals(Money.ZERO, Balance.Settled.amount)
    }

    @Test
    fun `one kopeck either way is not settled`() {
        assertEquals(Balance.Owed(Money(1)), Balance.of(Money(1)))
        assertEquals(Balance.Credit(Money(1)), Balance.of(Money(-1)))
    }
}
