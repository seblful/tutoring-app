package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats whole units without decimals`() {
        assertEquals("1500 BYN", Money.ofMajor(1500).format(Currency.BYN, ','))
    }

    @Test
    fun `formats minor units with two decimal places`() {
        assertEquals("1500,50 BYN", Money(150050).format(Currency.BYN, ','))
        assertEquals("0,05 BYN", Money(5).format(Currency.BYN, ','))
    }

    @Test
    fun `formats negative amounts with a leading sign`() {
        assertEquals("-300 BYN", Money.ofMajor(-300).format(Currency.BYN, ','))
    }

    /**
     * The separator is the *reader's*, and the amount is the same number either way — which is the whole
     * point of it being a parameter rather than a constant. See [Money.amountText] for why it has no
     * default.
     */
    @Test
    fun `the decimal separator is the caller's`() {
        assertEquals("1500.50 USD", Money(150050).format(Currency.USD, '.'))
        assertEquals("1500,50 USD", Money(150050).format(Currency.USD, ','))
        assertEquals("-0.50", Money(-50).amountText('.'))
        assertEquals("a whole amount has no separator to write", "1500", Money.ofMajor(1500).amountText('.'))
    }

    /** Every currency captions the same stored number — nothing is converted. */
    @Test
    fun `a currency only labels the amount`() {
        val amount = Money(150050)
        assertEquals(
            Currency.entries.map { "1500,50 ${it.name}" },
            Currency.entries.map { amount.format(it, ',') },
        )
    }

    @Test
    fun `arithmetic adds and subtracts minor units`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-50), Money(100) - Money(150))
        assertTrue((Money(100) - Money(150)).isNegative)
    }
}
