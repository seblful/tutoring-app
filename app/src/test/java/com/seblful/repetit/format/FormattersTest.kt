package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Covers the month-key encoding and the amount-input formatter not pinned elsewhere. */
class FormattersTest {

    private fun keyOf(year: Int, month: Int) = year * 12 + (month - 1)

    @Test
    fun `formatMonthKey decodes the year and month and labels it`() {
        val jan = formatMonthKey(keyOf(2026, 1))
        val dec = formatMonthKey(keyOf(2026, 12))
        val nextJan = formatMonthKey(keyOf(2027, 1))

        assertTrue("January 2026 carries its year", jan.contains("2026"))
        assertTrue("December 2026 carries its year", dec.contains("2026"))
        assertTrue("January 2027 rolls over the year", nextJan.contains("2027"))
        assertNotEquals("different months produce different labels", jan, dec)
        assertTrue("month label is capitalised", jan.first().isUpperCase())
    }

    @Test
    fun `month key is consistent with the encoding used by Reports`() {
        // Reports encodes as year * 12 + (monthValue - 1); decoding must invert it.
        val date = LocalDate.of(2026, 6, 1)
        val key = date.year * 12 + (date.monthValue - 1)
        assertTrue(formatMonthKey(key).contains("2026"))
    }

    @Test
    fun `formatMonth labels a month with its year, capitalised`() {
        val june = formatMonth(YearMonth.of(2026, 6))

        assertTrue("carries the year", june.contains("2026"))
        assertTrue("capitalised", june.first().isUpperCase())
        assertNotEquals(june, formatMonth(YearMonth.of(2026, 7)))
        assertNotEquals("the year is part of the label", june, formatMonth(YearMonth.of(2027, 6)))
    }

    @Test
    fun `formatMonth agrees with the month-key label`() {
        assertEquals(formatMonthKey(keyOf(2026, 6)), formatMonth(YearMonth.of(2026, 6)))
    }

    @Test
    fun `weekday short names are distinct and abbreviated`() {
        val names = DayOfWeek.entries.map { it.shortName() }

        assertEquals("пн", DayOfWeek.MONDAY.shortName())
        assertEquals("all seven differ", 7, names.toSet().size)
        assertTrue("stay short enough for a calendar header", names.all { it.length <= 4 })
    }

    @Test
    fun `toAmountInput is the inverse of parseMoneyOrNull for representable amounts`() {
        listOf(Money(0), Money(5), Money(50), Money(3050), Money(150000)).forEach { money ->
            assertEquals(money, parseMoneyOrNull(money.toAmountInput()))
        }
    }
}
