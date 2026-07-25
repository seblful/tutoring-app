package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun `hoursLabelOrNull reads whole and half hours as hours, everything else as minutes`() {
        assertEquals("1", hoursLabelOrNull(60))
        assertEquals("1,5", hoursLabelOrNull(90))
        assertEquals("2", hoursLabelOrNull(120))
        assertEquals("2,5", hoursLabelOrNull(150))
        assertNull("sub-hour durations stay in minutes", hoursLabelOrNull(30))
        assertNull("45 min isn't a clean hour count", hoursLabelOrNull(45))
        assertNull("nor is an odd count past an hour", hoursLabelOrNull(75))
    }

    @Test
    fun `every duration the Settings controls offer keeps the label it had before`() {
        // The segmented controls offer exactly these; pinned so a formatting change is visible here.
        val labels = listOf(30, 45, 60, 90, 120).associateWith { hoursLabelOrNull(it) }
        assertEquals(mapOf(30 to null, 45 to null, 60 to "1", 90 to "1,5", 120 to "2"), labels)
    }

    @Test
    fun `toAmountInput is the inverse of parseMoneyOrNull for representable amounts`() {
        listOf(Money(0), Money(5), Money(50), Money(3050), Money(150000)).forEach { money ->
            assertEquals(money, parseMoneyOrNull(money.toAmountInput()))
        }
    }
}
