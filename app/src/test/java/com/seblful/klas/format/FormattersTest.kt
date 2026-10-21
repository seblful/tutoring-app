package com.seblful.klas.format

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class FormattersTest {

    @get:Rule
    val locale = AppLocaleRule.russian()

    private fun keyOf(year: Int, month: Int) = year * 12 + (month - 1)

    @Test
    fun `month key is decoded with the encoding used by Reports`() {
        val date = LocalDate(2026, 6, 1)
        val key = date.year * 12 + (date.month.number - 1)
        assertEquals("Июнь", formatMonthName(key))
        assertEquals("Январь", formatMonthName(keyOf(2027, 1)))
        assertEquals("Декабрь", formatMonthName(keyOf(2026, 12)))
    }

    @Test
    fun `formatMonth labels a month with its year, capitalised`() {
        val june = formatMonth(YearMonth(2026, 6))

        assertTrue("carries the year", june.contains("2026"))
        assertTrue("capitalised", june.first().isUpperCase())
        assertNotEquals(june, formatMonth(YearMonth(2026, 7)))
        assertNotEquals("the year is part of the label", june, formatMonth(YearMonth(2027, 6)))
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
        val label = formatWeekRange(LocalDate(2026, 7, 6), LocalDate(2026, 7, 12))

        assertEquals("6 – 12 июля", label)
    }

    @Test
    fun `a week crossing a month names both months but no year`() {
        val label = formatWeekRange(LocalDate(2026, 6, 29), LocalDate(2026, 7, 5))

        assertEquals("29 июня – 5 июля", label)
    }

    @Test
    fun `a week crossing a year carries both years`() {
        val label = formatWeekRange(LocalDate(2026, 12, 28), LocalDate(2027, 1, 3))

        assertTrue("names the old year", label.contains("2026"))
        assertTrue("names the new year", label.contains("2027"))
    }

    @Test
    fun `every week range stays short enough for the navigator`() {
        val worst = listOf(
            LocalDate(2026, 9, 28) to LocalDate(2026, 10, 4),
            LocalDate(2026, 12, 28) to LocalDate(2027, 1, 3),
        )
        worst.forEach { (start, end) ->
            val label = formatWeekRange(start, end)
            assertTrue("«$label» is too long for the navigator", label.length <= 26)
        }
    }

    @Test
    fun `the 24-hour clock is zero-padded and markerless`() {
        assertEquals("21:00", LocalTime(21, 0).formatTime(hour12 = false))
        assertEquals("09:05", LocalTime(9, 5).formatTime(hour12 = false))
        assertEquals("00:30", LocalTime(0, 30).formatTime(hour12 = false))
    }

    @Test
    fun `the 12-hour clock names the half of the day, midnight and noon included`() {
        assertEquals("9:00 PM", LocalTime(21, 0).formatTime(hour12 = true))
        assertEquals("9:05 AM", LocalTime(9, 5).formatTime(hour12 = true))
        assertEquals("12:00 AM", LocalTime(0, 0).formatTime(hour12 = true))
        assertEquals("12:30 PM", LocalTime(12, 30).formatTime(hour12 = true))
        assertEquals("11:59 PM", LocalTime(23, 59).formatTime(hour12 = true))
    }

    @Test
    fun `an hour on a scale is unpadded, and without minutes on a 12-hour clock`() {
        val zone = TimeZone.currentSystemDefault()
        val at = { hour: Int -> LocalDate(2026, 7, 29).atTime(hour, 0).toInstant(zone) }

        assertEquals("9:00", at(9).formatHour(hour12 = false))
        assertEquals("21:00", at(21).formatHour(hour12 = false))
        assertEquals("9 AM", at(9).formatHour(hour12 = true))
        assertEquals("12 PM", at(12).formatHour(hour12 = true))
    }

    @Test
    fun `a 12-hour date and time keeps the date and gains the marker`() {
        val at = Instant.parse("2026-07-29T09:00:00Z")

        val label = at.formatDateTime(hour12 = true)

        assertTrue("«$label» says neither AM nor PM", label.endsWith("AM") || label.endsWith("PM"))
        assertTrue("«$label» has lost its date", label.contains("июл"))
        assertNotEquals("the two faces are the same label", at.formatDateTime(hour12 = false), label)
    }

    @Test
    fun `toAmountInput is the inverse of parseMoneyOrNull for representable amounts`() {
        listOf(Money(0), Money(5), Money(50), Money(3050), Money(150000)).forEach { money ->
            assertEquals(money, parseMoneyOrNull(money.toAmountInput()))
        }
    }

    @Test
    fun `an amount typed past two decimals rounds to the nearest kopeck`() {
        assertEquals(Money(200), parseMoneyOrNull("1,999"))
        assertEquals("half goes up", Money(124), parseMoneyOrNull("1,235"))
        assertEquals(Money(4505), parseMoneyOrNull("45,05"))
        assertEquals(Money(5), parseMoneyOrNull("0,05"))
    }

    @Test
    fun `a negative amount under one ruble keeps its sign`() {
        assertEquals("-0,50", Money(-50).toAmountInput())
        assertEquals("-0,05", Money(-5).toAmountInput())
        assertEquals("-1,50", Money(-150).toAmountInput())
    }

    @Test
    fun `hours print whole where they are whole and to a tenth where they are not`() {
        assertEquals("0", formatHours(0))
        assertEquals("1", formatHours(60))
        assertEquals("1,5", formatHours(90))
        assertEquals("0,5", formatHours(30))
        assertEquals("42", formatHours(2520))
        assertEquals("42,5", formatHours(2550))
        assertEquals("0,8", formatHours(45))
        assertEquals("0,3", formatHours(20))
    }

    @Test
    fun `an amount above the ceiling is not an amount`() {
        assertNull(parseMoneyOrNull("2000000000000000"))
        assertNull(parseMoneyOrNull(Money(Money.MAX.minor + 1).amountText(',')))
    }

    @Test
    fun `the ceiling itself parses, and still bills a positive charge over the longest lesson`() {
        assertEquals(Money.MAX, parseMoneyOrNull(Money.MAX.amountText(',')))

        val charge = ClassRules.chargeFor(Money.MAX, durationMinutes = 24 * 60)
        assertTrue("the ceiling exists so this cannot wrap", charge.minor > 0)
    }

    @Test
    fun `ordinary tutoring amounts are unaffected by the ceiling`() {
        assertEquals(Money.ofMajor(30), parseMoneyOrNull("30"))
        assertEquals(Money.ofMajor(2_500), parseMoneyOrNull("2500"))
    }

    @Test
    fun `facts about a lesson go on one line, separated by a bullet`() {
        assertEquals("21:00 · Математика · Иван Петров", bulletJoin("21:00", "Математика", "Иван Петров"))
    }

    @Test
    fun `a blank part is dropped rather than leaving a stranded separator`() {
        assertEquals("21:00 · Математика", bulletJoin("21:00", "", "Математика"))
        assertEquals("21:00 · Математика", bulletJoin("21:00", "   ", "Математика"))
    }

    @Test
    fun `one part needs no separator and none needs no string`() {
        assertEquals("21:00", bulletJoin("21:00"))
        assertEquals("", bulletJoin())
        assertEquals("", bulletJoin("", " "))
    }

    @Test
    fun `a list joins the same way the arguments do`() {
        assertEquals(
            bulletJoin("4 занятия", "5 ч", "240 BYN"),
            bulletJoin(listOf("4 занятия", "5 ч", "240 BYN")),
        )
    }
}
