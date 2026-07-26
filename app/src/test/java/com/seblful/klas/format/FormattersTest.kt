package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
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
    fun `a week inside one month names the month once`() {
        val label = formatWeekRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))

        assertEquals("6 – 12 июля", label)
    }

    @Test
    fun `a week crossing a month names both months but no year`() {
        val label = formatWeekRange(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 5))

        assertEquals("29 июня – 5 июля", label)
    }

    @Test
    fun `a week crossing a year carries both years`() {
        val label = formatWeekRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3))

        assertTrue("names the old year", label.contains("2026"))
        assertTrue("names the new year", label.contains("2027"))
    }

    @Test
    fun `every week range stays short enough for the navigator`() {
        // Worst case is a week spanning the two longest month names, or a year boundary.
        val worst = listOf(
            LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 12, 28) to LocalDate.of(2027, 1, 3),
        )
        worst.forEach { (start, end) ->
            val label = formatWeekRange(start, end)
            assertTrue("«$label» is too long for the navigator", label.length <= 26)
        }
    }

    @Test
    fun `toAmountInput is the inverse of parseMoneyOrNull for representable amounts`() {
        listOf(Money(0), Money(5), Money(50), Money(3050), Money(150000)).forEach { money ->
            assertEquals(money, parseMoneyOrNull(money.toAmountInput()))
        }
    }

    /**
     * A stat tile is a summary, so a whole number of hours prints as one — «42 ч», not «42,0 ч» — and
     * anything else rounds to a tenth with a Russian comma.
     */
    @Test
    fun `hours print whole where they are whole and to a tenth where they are not`() {
        assertEquals("0", formatHours(0))
        assertEquals("1", formatHours(60))
        assertEquals("1,5", formatHours(90))
        assertEquals("0,5", formatHours(30))
        assertEquals("42", formatHours(2520))
        assertEquals("42,5", formatHours(2550))
        // 45 minutes is 0.75 h, which rounds to the nearest tenth rather than truncating to 0,7.
        assertEquals("0,8", formatHours(45))
        // 20 minutes is 0.333… h.
        assertEquals("0,3", formatHours(20))
    }
}
