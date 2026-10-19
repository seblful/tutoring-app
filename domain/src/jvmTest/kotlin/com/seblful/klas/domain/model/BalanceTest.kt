package com.seblful.klas.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three-way question every surface showing a balance used to ask for itself, inside a `@Composable`.
 *
 * These cases are why the type exists: each of them was decided in two or three places, and the sign flip
 * in [`an over-payment is credit, and its amount is positive`] was written out twice by hand.
 */
class BalanceTest {

    @Test
    fun `nothing owed either way is settled`() {
        assertEquals(Balance.Settled, Balance.of(Money.ZERO))
    }

    @Test
    fun `a positive balance is what the student owes`() {
        assertEquals(Balance.Owed(Money.ofMajor(240)), Balance.of(Money.ofMajor(240)))
    }

    /**
     * The flip lives here and nowhere else. `Money.ZERO - balance` was spelled independently in
     * `balanceDescription` and in the student card's hero, which is two chances to print «Переплата −50».
     */
    @Test
    fun `an over-payment is credit, and its amount is positive`() {
        val credit = Balance.of(Money.ofMajor(-50))

        assertEquals(Balance.Credit(Money.ofMajor(50)), credit)
        assertEquals(Money.ofMajor(50), credit.amount, "positive, ready to print")
    }

    /**
     * A settled balance still has an amount, because the student card leads with this figure in the same
     * place in all three states and reads «Долг 0» when there is nothing owed — see `BalanceHero`.
     */
    @Test
    fun `a settled balance carries a zero to print`() {
        assertEquals(Money.ZERO, Balance.Settled.amount)
    }

    /** Kopecks, not just whole units — the smallest debt is still a debt. */
    @Test
    fun `one kopeck either way is not settled`() {
        assertEquals(Balance.Owed(Money(1)), Balance.of(Money(1)))
        assertEquals(Balance.Credit(Money(1)), Balance.of(Money(-1)))
    }
}
