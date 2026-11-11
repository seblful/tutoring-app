package com.seblful.klas.format

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FormattersLanguageTest {

    @Test
    fun `a month is named in the language the app is set to`() {
        inLanguage(RUSSIAN) {
            assertEquals("Июнь 2026", formatMonth(YearMonth(2026, 6)))
        }
        inLanguage(ENGLISH) {
            assertEquals("June 2026", formatMonth(YearMonth(2026, 6)))
        }
    }

    @Test
    fun `a month heading is capitalised in both languages`() {
        listOf(RUSSIAN, ENGLISH).forEach { locale ->
            inLanguage(locale) {
                assertTrue(
                    formatMonth(YearMonth(2026, 6)).first().isUpperCase(),
                    "«${formatMonth(YearMonth(2026, 6))}» does not start with a capital",
                )
                assertTrue(formatMonthName(5).first().isUpperCase())
            }
        }
    }

    @Test
    fun `a calendar header's weekdays are the language's own abbreviations`() {
        inLanguage(RUSSIAN) {
            // Android's tables write «пн», Foundation's «Пн»: both are Russian's own, so the case is the platform's.
            assertEquals("пн", DayOfWeek.MONDAY.shortName().lowercase())
        }
        inLanguage(ENGLISH) {
            assertEquals("Mon", DayOfWeek.MONDAY.shortName())
        }
    }

    @Test
    fun `a week range stays short enough for the navigator in English too`() {
        inLanguage(ENGLISH) {
            listOf(
                LocalDate(2026, 9, 28) to LocalDate(2026, 10, 4),
                LocalDate(2026, 12, 28) to LocalDate(2027, 1, 3),
            ).forEach { (start, end) ->
                val label = formatWeekRange(start, end)
                assertTrue(label.length <= 26, "«$label» is too long for the navigator")
            }
            assertEquals("July 6 – 12", formatWeekRange(LocalDate(2026, 7, 6), LocalDate(2026, 7, 12)))
        }
    }

    @Test
    fun `a week range in a language that wraps its day in text names the month at both ends`() {
        inLanguage("ja") {
            assertEquals("7月6日 – 7月12日", formatWeekRange(LocalDate(2026, 7, 6), LocalDate(2026, 7, 12)))
        }
    }

    /** Foundation writes Japanese «10月14日 水曜日» where `java.text` writes no space; the order is the same. */
    private fun String.withoutPlatformSpacing(locale: String) = if (locale == "ja") replace(" ", "") else this

    @Test
    fun `a date is ordered the way its language writes one`() {
        val date = LocalDate(2026, 10, 14)
        val expected = mapOf(
            RUSSIAN to listOf("14 окт. 2026", "14 окт.", "Среда, 14 октября"),
            ENGLISH to listOf("Oct 14, 2026", "Oct 14", "Wednesday, October 14"),
            "de" to listOf("14. Okt. 2026", "14. Okt.", "Mittwoch, 14. Oktober"),
            "ja" to listOf("2026年10月14日", "10月14日", "10月14日水曜日"),
            "ko" to listOf("2026년 10월 14일", "10월 14일", "10월 14일 수요일"),
            "tr" to listOf("14 Eki 2026", "14 Eki", "14 Ekim Çarşamba"),
        )
        expected.forEach { (locale, forms) ->
            val (short, near, day) = forms
            inLanguage(locale) {
                assertEquals(short, date.formatDate(), locale)
                assertEquals(near, date.formatNearDate(thisYear = 2026), locale)
                assertEquals(day, date.formatDay().withoutPlatformSpacing(locale), locale)
            }
        }
    }

    @Test
    fun `every app language has a day-and-month without a year`() {
        for (language in AppLanguage.translated) {
            inLanguage(language.tag!!) {
                val near = LocalDate(2026, 10, 14).formatNearDate(thisYear = 2026)
                assertTrue("2026" !in near, "${language.tag}: «$near» carries the year")
                assertTrue("14" in near, "${language.tag}: «$near» lost the day")
            }
        }
    }

    @Test
    fun `money is written with the language's decimal separator - whatever the currency`() {
        inLanguage(RUSSIAN) {
            assertEquals(',', AppLocale.decimalSeparator)
            assertEquals("1 500,50\u00A0$", Money(150050).money(Currency.USD))
            assertEquals("1500,50", Money(150050).amount())
            assertEquals("-0,50", Money(-50).toAmountInput())
        }
        inLanguage(ENGLISH) {
            assertEquals('.', AppLocale.decimalSeparator)
            assertEquals("\u20C51,500.50", Money(150050).money(Currency.BYN))
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
        inLanguage(RUSSIAN) {
            assertEquals("16 037,50\u00A0\u20C5", total.money(Currency.BYN))
            assertEquals(total, parseMoneyOrNull(total.toAmountInput()))
        }
        inLanguage(ENGLISH) {
            assertEquals("\$16,037.50", total.money(Currency.USD))
            assertEquals(total, parseMoneyOrNull(total.toAmountInput()))
        }
    }

    /** Arabic's «٬» and «٫» go with Arabic-Indic digits; the app writes Latin ones, so it takes their separators. */
    @Test
    fun `an Arabic amount is separated the way its Latin digits are`() {
        inLanguage("ar") {
            assertEquals('.', AppLocale.decimalSeparator)
            assertEquals("16,037.50 SAR", Money(1_603_750).money(Currency.SAR))
        }
    }

    @Test
    fun `system text uses a readable fallback for the bundled sign`() {
        val locale = ENGLISH
        assertEquals("BYN 1,500.50", Money(150050).money(Currency.BYN, locale).forSystemFont())
        assertEquals("-BYN 1,500.50", Money(-150050).money(Currency.BYN, locale).forSystemFont())
        val russian = Money(150050).money(Currency.BYN, RUSSIAN).forSystemFont()
        assertTrue(russian.endsWith("500,50\u00A0BYN"), "«$russian» keeps its sign after the amount")
        assertEquals("₽1,500.50", Money(150050).money(Currency.RUB, locale).forSystemFont())
        assertEquals("\$1,500.50", Money(150050).money(Currency.USD, locale).forSystemFont())
    }

    /** An hour count is a number too, and was the one place the comma was written into the arithmetic. */
    @Test
    fun `an hour count follows the same separator`() {
        inLanguage(RUSSIAN) { assertEquals("1,5", formatHours(90)) }
        inLanguage(ENGLISH) { assertEquals("1.5", formatHours(90)) }
        // Whole hours have no separator to disagree about.
        inLanguage(ENGLISH) { assertEquals("42", formatHours(2520)) }
    }

    @Test
    fun `the currency sign goes on the side the language writes it`() {
        val amount = Money(150050)
        inLanguage("de") { assertEquals("1.500,50\u00A0€", amount.money(Currency.EUR)) }
        inLanguage("nl") { assertEquals("€\u00A01.500,50", amount.money(Currency.EUR)) }
        inLanguage("ja") { assertEquals("¥1,500.50", amount.money(Currency.JPY)) }
        inLanguage("ar") { assertTrue(amount.money(Currency.SAR).endsWith("\u00A0SAR")) }
        inLanguage(ENGLISH) { assertEquals("NOK\u00A01,500.50", amount.money(Currency.NOK)) }
    }

    @Test
    fun `every app language writes every currency`() {
        for (language in AppLanguage.translated) {
            val locale = language.tag!!
            Currency.entries.forEach { currency ->
                val text = Money(150050).money(currency, locale)
                assertTrue(currency.symbol in text && "50" in text, "${language.tag} $currency: «$text»")
            }
        }
    }

    /**
     * A field seeded in one language still reads after the tutor changes it, because the parse takes both
     * separators. Without that, switching to English would have left every money field the app had already
     * filled in — a pre-filled rate, a payment being corrected — unreadable and outlined in red.
     */
    @Test
    fun `an amount seeded in one language parses in the other`() {
        val seeded = inLanguage(RUSSIAN) { Money(2550).toAmountInput() }

        inLanguage(ENGLISH) {
            assertEquals(Money(2550), parseMoneyOrNull(seeded), "25,50 is still an amount to an English reader")
            assertEquals(Money(2550), parseMoneyOrNull("25.50"))
        }
    }

    @Test
    fun `seeded amounts round trip in every supported app language`() {
        for (language in AppLanguage.translated) {
            inLanguage(language.tag!!) {
                for (amount in listOf(Money.ZERO, Money(1), Money(2550), Money.MAX)) {
                    assertEquals(amount, parseMoneyOrNull(amount.toAmountInput()), language.tag)
                }
            }
        }
    }

    /** The clock is the phone's answer and **not** the language's — it must not move with this. */
    @Test
    fun `the 24-hour face is the same in both languages`() {
        val nine = LocalTime(21, 0)
        val russian = inLanguage(RUSSIAN) { nine.formatTime(hour12 = false) }
        val english = inLanguage(ENGLISH) { nine.formatTime(hour12 = false) }

        assertEquals("21:00", russian)
        assertEquals(russian, english, "a clock face is not a language")
    }

    /** A 24-hour language keeps the pinned Latin pair, for the reason `Formats.time12` gives. */
    @Test
    fun `the 12-hour marker is AM and PM in both languages`() {
        listOf(RUSSIAN, ENGLISH, "ja").forEach { locale ->
            inLanguage(locale) {
                assertEquals("9:00 PM", LocalTime(21, 0).formatTime(hour12 = true))
                assertEquals("12:00 AM", LocalTime(0, 0).formatTime(hour12 = true))
            }
        }
    }

    @Test
    fun `a language with its own 12-hour clock writes it its own way`() {
        val evening = LocalTime(21, 5)
        inLanguage("ko") { assertEquals("오후 9:05", evening.formatTime(hour12 = true)) }
        inLanguage("zh-TW") { assertEquals("下午9:05", evening.formatTime(hour12 = true)) }
        inLanguage("ar") { assertTrue(evening.formatTime(hour12 = true).endsWith("م")) }
        // A 12-hour language's 24-hour face is still the phone's call, not the language's.
        inLanguage("ko") { assertEquals("21:05", evening.formatTime(hour12 = false)) }
    }

    @Test
    fun `every app language formats a 12-hour time without failing`() {
        for (language in AppLanguage.translated) {
            inLanguage(language.tag!!) {
                val label = LocalTime(21, 5).formatTime(hour12 = true)
                assertTrue("9" in label && "05" in label, "${language.tag}: «$label»")
            }
        }
    }

    /**
     * Each language's formatter set is built once and kept, so the switch is not paid for per call — and,
     * more to the point, switching back has to give the *same* answer rather than a re-derived one.
     */
    @Test
    fun `switching back gives the same answer as before`() {
        val first = inLanguage(RUSSIAN) { formatMonth(YearMonth(2026, 6)) }
        val english = inLanguage(ENGLISH) { formatMonth(YearMonth(2026, 6)) }
        val again = inLanguage(RUSSIAN) { formatMonth(YearMonth(2026, 6)) }

        assertEquals(first, again)
        assertNotEquals(first, english)
    }
}
