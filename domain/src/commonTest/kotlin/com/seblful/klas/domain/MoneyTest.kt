package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `formats whole units without decimals`() {
        assertEquals("1 500 \u20C5", Money.ofMajor(1500).format(Currency.BYN, ',', ' '))
    }

    @Test
    fun `formats minor units with two decimal places`() {
        assertEquals("1 500,50 \u20C5", Money(150050).format(Currency.BYN, ',', ' '))
        assertEquals("0,05 \u20C5", Money(5).format(Currency.BYN, ',', ' '))
    }

    @Test
    fun `formats negative amounts with a leading sign`() {
        assertEquals("-300 \u20C5", Money.ofMajor(-300).format(Currency.BYN, ',', ' '))
        assertEquals("-1 300 \u20C5", Money.ofMajor(-1300).format(Currency.BYN, ',', ' '))
    }

    @Test
    fun `the decimal separator is the caller's`() {
        assertEquals("1,500.50 $", Money(150050).format(Currency.USD, '.', ','))
        assertEquals("1 500,50 $", Money(150050).format(Currency.USD, ',', ' '))
        assertEquals("-0.50", Money(-50).amountText('.'))
        assertEquals("1500", Money.ofMajor(1500).amountText('.'), "a whole amount has no separator to write")
    }

    /**
     * Thousands are grouped for the reader, in threes from the decimal point, and only the whole part —
     * the kopecks are two digits and never split.
     */
    @Test
    fun `the whole part is grouped in threes`() {
        assertEquals("999", Money.ofMajor(999).displayText(',', ' '))
        assertEquals("1 000", Money.ofMajor(1000).displayText(',', ' '))
        assertEquals("16 037,50", Money(1_603_750).displayText(',', ' '))
        assertEquals("1,000,000.00".removeSuffix(".00"), Money.MAX.displayText('.', ','))
        assertEquals("-12,345.67", Money(-1_234_567).displayText('.', ','))
    }

    /** The editable text stays ungrouped: a field seeded `1,500` would read back as 1.5. */
    @Test
    fun `the amount a field is seeded with is never grouped`() {
        assertEquals("1500,50", Money(150050).amountText(','))
        assertEquals("1000000", Money.MAX.amountText('.'))
    }

    /** Every currency captions the same stored number — nothing is converted. */
    @Test
    fun `a currency only labels the amount`() {
        val amount = Money(150050)
        Currency.entries.forEach { currency ->
            assertEquals("1 500,50 ${currency.symbol}", amount.format(currency, ',', ' '))
        }
    }

    @Test
    fun `a whole unit is the minor-unit factor`() {
        assertEquals(Money.MINOR_PER_MAJOR, Money.ofMajor(1).minor)
        assertEquals("1,05", Money(Money.MINOR_PER_MAJOR + 5).amountText(','))
    }

    @Test
    fun `arithmetic adds and subtracts minor units`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-50), Money(100) - Money(150))
        assertTrue((Money(100) - Money(150)).isNegative)
    }
}
