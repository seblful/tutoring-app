package com.seblful.klas.format

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Covers the month-key encoding and the amount-input formatter not pinned elsewhere.
 *
 * **In Russian, said out loud.** Every expectation below — «пн», «6 – 12 июля», `-0,50` — is a fact about
 * one of the app's two languages, and the formatters read the ambient [com.seblful.klas.format.AppLocale].
 * Without the rule that would be the *build machine's* locale, so this file would assert Russian output on a
 * Russian workstation and fail on an English CI agent. `FormattersLanguageTest` is the other half: that the
 * same functions follow the setting rather than merely producing these strings.
 */
class FormattersTest {

    @get:Rule
    val locale = AppLocaleRule.russian()

    private fun keyOf(year: Int, month: Int) = year * 12 + (month - 1)

    @Test
    fun `month key is decoded with the encoding used by Reports`() {
        // Reports encodes as year * 12 + (monthValue - 1); decoding must invert it.
        val date = LocalDate.of(2026, 6, 1)
        val key = date.year * 12 + (date.monthValue - 1)
        assertEquals("Июнь", formatMonthName(key))
        assertEquals("Январь", formatMonthName(keyOf(2027, 1)))
        assertEquals("Декабрь", formatMonthName(keyOf(2026, 12)))
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

    /** The face a 24-hour phone gets, and the one a report keeps: zero-padded, no marker. */
    @Test
    fun `the 24-hour clock is zero-padded and markerless`() {
        assertEquals("21:00", LocalTime.of(21, 0).formatTime(hour12 = false))
        assertEquals("09:05", LocalTime.of(9, 5).formatTime(hour12 = false))
        assertEquals("00:30", LocalTime.of(0, 30).formatTime(hour12 = false))
    }

    /**
     * The 12-hour face, for a phone whose clock is set that way (`ui/common/timeText` asks).
     *
     * Pinned here rather than left to the device, because the marker is the part that varies by platform
     * — `ru` CLDR has rendered it both «ПП» and "PM" — and because the hours a 12-hour clock has to get
     * right are the two it renumbers: midnight is 12 AM, not 0 AM, and noon is 12 PM, not 0 PM.
     */
    @Test
    fun `the 12-hour clock names the half of the day, midnight and noon included`() {
        assertEquals("9:00 PM", LocalTime.of(21, 0).formatTime(hour12 = true))
        assertEquals("9:05 AM", LocalTime.of(9, 5).formatTime(hour12 = true))
        assertEquals("12:00 AM", LocalTime.MIDNIGHT.formatTime(hour12 = true))
        assertEquals("12:30 PM", LocalTime.of(12, 30).formatTime(hour12 = true))
        assertEquals("11:59 PM", LocalTime.of(23, 59).formatTime(hour12 = true))
    }

    /** The date-and-time label carries the marker through — a group's lesson list prints this one. */
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

    /**
     * A rate is typed, so it can be typed to more precision than money has. Kopecks are the smallest
     * unit there is, so a third decimal has to go somewhere, and it rounds rather than truncating —
     * truncating would quietly forgive a kopeck on every such amount.
     */
    @Test
    fun `an amount typed past two decimals rounds to the nearest kopeck`() {
        assertEquals(Money(200), parseMoneyOrNull("1,999"))
        assertEquals("half goes up", Money(124), parseMoneyOrNull("1,235"))
        assertEquals(Money(4505), parseMoneyOrNull("45,05"))
        assertEquals(Money(5), parseMoneyOrNull("0,05"))
    }

    /**
     * A balance is the one amount that goes below zero, and between −1 and 0 the whole-ruble part is
     * "0" — so the sign is the only thing distinguishing a small debt from a small credit, and
     * formatting the two halves separately used to drop it.
     */
    @Test
    fun `a negative amount under one ruble keeps its sign`() {
        assertEquals("-0,50", Money(-50).toAmountInput())
        assertEquals("-0,05", Money(-5).toAmountInput())
        assertEquals("-1,50", Money(-150).toAmountInput())
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

    // --- The one ceiling on how large an amount can be -------------------------------------------
    //
    // Fitting in a Long is not the bar: `ClassRules.chargeFor` computes `rate × minutes`, so a rate
    // that fits can still bill a wrapped, negative charge. Rejecting the input is the only place the
    // problem is still legible — see `Money.MAX`.

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

    // ── bulletJoin ───────────────────────────────────────────────────────────────────────────────
    // The app's one separator. It was private to `AndroidNotifier`, where the schedule's day summary
    // could not reach it and so spelled the same " · " for itself.

    @Test
    fun `facts about a lesson go on one line, separated by a bullet`() {
        assertEquals("21:00 · Математика · Иван Петров", bulletJoin("21:00", "Математика", "Иван Петров"))
    }

    /** What makes it safe to pass everything that *might* be known — an unnamed group, say. */
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
