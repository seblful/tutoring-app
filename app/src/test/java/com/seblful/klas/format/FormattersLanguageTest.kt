package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * **The formatters follow the app's language**, which is the thing adding a second language had to buy.
 *
 * `FormattersTest` pins the Russian output. This asks the question that file cannot: whether the same
 * functions produce a *different* answer when the setting moves — because the failure mode being guarded
 * against is not a wrong month name, it is an app whose strings are in English and whose dates are in
 * Russian. That was the state before [AppLocale] existed: the formatters were module-level `val`s built once
 * at class load against a pinned `ru`, and no setting could reach them.
 *
 * The locale is moved directly rather than through a settings row, which is the seam [AppLocale] exists to
 * be: `SettingsRepository` publishes it and everything below reads it, so the two halves are tested apart —
 * `SettingsRepositoryTest` that the row publishes, this that the formatters obey.
 */
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
            assertEquals("Июнь 2026", formatMonth(YearMonth.of(2026, 6)))
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals("June 2026", formatMonth(YearMonth.of(2026, 6)))
        }
    }

    /** English capitalises its month names already, so the heading rule is a no-op there and not a bug. */
    @Test
    fun `a month heading is capitalised in both languages`() {
        listOf(AppLocaleRule.RUSSIAN, AppLocaleRule.ENGLISH).forEach { locale ->
            inLanguage(locale) {
                assertTrue(
                    "«${formatMonth(YearMonth.of(2026, 6))}» does not start with a capital",
                    formatMonth(YearMonth.of(2026, 6)).first().isUpperCase(),
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

    /**
     * The week label has to stay short enough to sit between the navigator's arrows in **either**
     * language, which is a real constraint and not a formality: it is asserted at 26 characters for
     * Russian, and English's own worst case (two September/December dates and two years) has to clear the
     * same bar.
     */
    @Test
    fun `a week range stays short enough for the navigator in English too`() {
        inLanguage(AppLocaleRule.ENGLISH) {
            listOf(
                LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 12, 28) to LocalDate.of(2027, 1, 3),
            ).forEach { (start, end) ->
                val label = formatWeekRange(start, end)
                assertTrue("«$label» is too long for the navigator", label.length <= 26)
            }
            assertEquals("6 – 12 July", formatWeekRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)))
        }
    }

    /**
     * The **decimal separator** is the same setting, which is why it lives beside the dates rather than
     * beside the currency: `1500,50 USD` is a Russian reader's dollars and `1500.50 BYN` an English
     * reader's rubles, and both are correct. What must never happen is one screen writing each way.
     */
    @Test
    fun `money is written with the language's decimal separator, whatever the currency`() {
        inLanguage(AppLocaleRule.RUSSIAN) {
            assertEquals(',', AppLocale.decimalSeparator)
            assertEquals("1500,50 USD", Money(150050).money(Currency.USD))
            assertEquals("1500,50", Money(150050).amount())
            assertEquals("-0,50", Money(-50).toAmountInput())
        }
        inLanguage(AppLocaleRule.ENGLISH) {
            assertEquals('.', AppLocale.decimalSeparator)
            assertEquals("1500.50 BYN", Money(150050).money(Currency.BYN))
            assertEquals("1500.50", Money(150050).amount())
            assertEquals("-0.50", Money(-50).toAmountInput())
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
        val russian = inLanguageValue(AppLocaleRule.RUSSIAN) { java.time.LocalTime.of(21, 0).formatTime(hour12 = false) }
        val english = inLanguageValue(AppLocaleRule.ENGLISH) { java.time.LocalTime.of(21, 0).formatTime(hour12 = false) }

        assertEquals("21:00", russian)
        assertEquals("a clock face is not a language", russian, english)
    }

    /** And the 12-hour marker stays the pinned Latin pair, for the reason `Formats.time12` gives. */
    @Test
    fun `the 12-hour marker is AM and PM in both languages`() {
        listOf(AppLocaleRule.RUSSIAN, AppLocaleRule.ENGLISH).forEach { locale ->
            inLanguage(locale) {
                assertEquals("9:00 PM", java.time.LocalTime.of(21, 0).formatTime(hour12 = true))
                assertEquals("12:00 AM", java.time.LocalTime.MIDNIGHT.formatTime(hour12 = true))
            }
        }
    }

    /**
     * Each language's formatter set is built once and kept, so the switch is not paid for per call — and,
     * more to the point, switching back has to give the *same* answer rather than a re-derived one.
     */
    @Test
    fun `switching back gives the same answer as before`() {
        val first = inLanguageValue(AppLocaleRule.RUSSIAN) { formatMonth(YearMonth.of(2026, 6)) }
        val english = inLanguageValue(AppLocaleRule.ENGLISH) { formatMonth(YearMonth.of(2026, 6)) }
        val again = inLanguageValue(AppLocaleRule.RUSSIAN) { formatMonth(YearMonth.of(2026, 6)) }

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
