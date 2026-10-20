package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FormattersLanguageTest {

    private fun inLanguage(locale: Locale, body: () -> Unit) {
        val previous = AppLocale.current
        AppLocale.current = locale
        try {
            body()
        } finally {
            AppLocale.current = previous
        }
    }

    @Test
    fun `a month is named in the language the app is set to`() {
        inLanguage(AppLocaleRule.RUSSIAN) {
            assertEquals("Июнь 2026", formatMonth(YearMonth(2026, 6)))
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals("June 2026", formatMonth(YearMonth(2026, 6)))
        }
    }

    @Test
    fun `a month heading is capitalised in both languages`() {
        listOf(AppLocaleRule.RUSSIAN, AppLocaleRule.ENGLISH).forEach { locale ->
            inLanguage(locale) {
                assertTrue(
                    "«${formatMonth(YearMonth(2026, 6))}» does not start with a capital",
                    formatMonth(YearMonth(2026, 6)).first().isUpperCase(),
                )
                assertTrue(formatMonthName(2026 * 12 + 5).first().isUpperCase())
            }
        }
    }

    @Test
    fun `a calendar header's weekdays are the language's own abbreviations`() {
        inLanguage(AppLocaleRule.RUSSIAN) {
            assertEquals("пн", DayOfWeek.MONDAY.shortName())
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals("Mon", DayOfWeek.MONDAY.shortName())
        }
    }

    @Test
    fun `a week range stays short enough for the navigator in English too`() {
        inLanguage(AppLocaleRule.ENGLISH) {
            listOf(
                LocalDate(2026, 9, 28) to LocalDate(2026, 10, 4),
                LocalDate(2026, 12, 28) to LocalDate(2027, 1, 3),
            ).forEach { (start, end) ->
                val label = formatWeekRange(start, end)
                assertTrue("«$label» is too long for the navigator", label.length <= 26)
            }
            assertEquals("6 – 12 July", formatWeekRange(LocalDate(2026, 7, 6), LocalDate(2026, 7, 12)))
        }
    }

    @Test
    fun `money is written with the language's decimal separator, whatever the currency`() {
        inLanguage(AppLocaleRule.RUSSIAN) {
            assertEquals(',', AppLocale.decimalSeparator)
            assertEquals("1 500,50 USD", Money(150050).money(Currency.USD))
            assertEquals("1500,50", Money(150050).amount())
            assertEquals("-0,50", Money(-50).toAmountInput())
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals('.', AppLocale.decimalSeparator)
            assertEquals("1,500.50 BYN", Money(150050).money(Currency.BYN))
            assertEquals("1500.50", Money(150050).amount())
            assertEquals("-0.50", Money(-50).toAmountInput())
        }
    }

    /**
     * A shown amount is grouped in thousands — a **no-break** space in Russian, so `16 037` never splits
     * across a line — and a typed one is not: an English field seeded `1,500` would read back as 1.5,
     * because to `parseMoneyOrNull` a comma is a decimal separator.
     */
    @Test
    fun `a shown amount is grouped in thousands and a typed one reads back as itself`() {
        val total = Money(1_603_750)
        inLanguage(AppLocaleRule.RUSSIAN) {
            assertEquals("16 037,50 BYN", total.money(Currency.BYN))
            assertEquals(total, parseMoneyOrNull(total.toAmountInput()))
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals("16,037.50 USD", total.money(Currency.USD))
            assertEquals(total, parseMoneyOrNull(total.toAmountInput()))
        }
    }

    /** An hour count is a number too, and was the one place the comma was written into the arithmetic. */
    @Test
    fun `an hour count follows the same separator`() {
        inLanguage(AppLocaleRule.RUSSIAN) { assertEquals("1,5", formatHours(90)) }
        inLanguage(AppLocaleRule.ENGLISH) { assertEquals("1.5", formatHours(90)) }
        // Whole hours have no separator to disagree about.
        inLanguage(AppLocaleRule.ENGLISH) { assertEquals("42", formatHours(2520)) }
    }

    /**
     * A field seeded in one language still reads after the tutor changes it, because the parse takes both
     * separators. Without that, switching to English would have left every money field the app had already
     * filled in — a pre-filled rate, a payment being corrected — unreadable and outlined in red.
     */
    @Test
    fun `an amount seeded in one language parses in the other`() {
        val seeded = inLanguageValue(AppLocaleRule.RUSSIAN) { Money(2550).toAmountInput() }

        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals("25,50 is still an amount to an English reader", Money(2550), parseMoneyOrNull(seeded))
            assertEquals(Money(2550), parseMoneyOrNull("25.50"))
        }
    }

    /** The clock is the phone's answer and **not** the language's — it must not move with this. */
    @Test
    fun `the 24-hour face is the same in both languages`() {
        val nine = LocalTime(21, 0)
        val russian = inLanguageValue(AppLocaleRule.RUSSIAN) { nine.formatTime(hour12 = false) }
        val english = inLanguageValue(AppLocaleRule.ENGLISH) { nine.formatTime(hour12 = false) }

        assertEquals("21:00", russian)
        assertEquals("a clock face is not a language", russian, english)
    }

    /** And the 12-hour marker stays the pinned Latin pair, for the reason `Formats.time12` gives. */
    @Test
    fun `the 12-hour marker is AM and PM in both languages`() {
        listOf(AppLocaleRule.RUSSIAN, AppLocaleRule.ENGLISH).forEach { locale ->
            inLanguage(locale) {
                assertEquals("9:00 PM", LocalTime(21, 0).formatTime(hour12 = true))
                assertEquals("12:00 AM", LocalTime(0, 0).formatTime(hour12 = true))
            }
        }
    }

    /**
     * Each language's formatter set is built once and kept, so the switch is not paid for per call — and,
     * more to the point, switching back has to give the *same* answer rather than a re-derived one.
     */
    @Test
    fun `switching back gives the same answer as before`() {
        val first = inLanguageValue(AppLocaleRule.RUSSIAN) { formatMonth(YearMonth(2026, 6)) }
        val english = inLanguageValue(AppLocaleRule.ENGLISH) { formatMonth(YearMonth(2026, 6)) }
        val again = inLanguageValue(AppLocaleRule.RUSSIAN) { formatMonth(YearMonth(2026, 6)) }

        assertEquals(first, again)
        assertNotEquals(first, english)
    }

    private fun <T> inLanguageValue(locale: Locale, body: () -> T): T {
        var result: T? = null
        inLanguage(locale) { result = body() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
