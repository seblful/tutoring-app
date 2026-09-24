package com.seblful.klas.format

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * **A month after a day number is in the genitive** — «24 сентября», «5 мая» — and a month on its own is
 * not («Сентябрь 2026»). The JVM suite (`FormattersTest`) asserts the same strings and cannot catch the
 * way this broke: there `java.time` is the JDK's, and on a phone it is core-library desugaring's, which
 * fills the *format* month names (`MMMM`, `MMM`) with the *stand-alone* ones. Every Russian date on the
 * schedule read «Четверг, 24 сентябрь» while every unit test passed. Asserted here because this is where
 * the answer lives — the same argument as `WeekStartKeywordTest`.
 */
@HiltAndroidTest
class MonthNameCaseTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    private var previous: Locale = Locale.ROOT

    @Before
    fun pinRussian() {
        previous = AppLocale.current
        AppLocale.current = Locale.forLanguageTag("ru")
    }

    @After
    fun restore() {
        AppLocale.current = previous
    }

    @Test
    fun aDayNamesItsMonthInTheGenitive() {
        assertEquals("Четверг, 24 сентября", LocalDate.of(2026, 9, 24).formatDay())
    }

    @Test
    fun aWeekNamesItsMonthsInTheGenitive() {
        assertEquals("21 – 27 сентября", formatWeekRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)))
        assertEquals("29 июня – 5 июля", formatWeekRange(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 5)))
    }

    /** May is the month whose short genitive differs from its short stand-alone name: «мая», not «май». */
    @Test
    fun aShortDateNamesItsMonthInTheGenitive() {
        assertEquals("5 мая 2026", LocalDate.of(2026, 5, 5).formatDate())
    }

    /** The other half: a month heading a page is still the stand-alone name. */
    @Test
    fun aMonthOnItsOwnStaysNominative() {
        assertEquals("Сентябрь 2026", formatMonth(YearMonth.of(2026, 9)))
    }

    /** English has one form, so it is only asked not to have been broken on the way. */
    @Test
    fun englishIsUnchanged() {
        AppLocale.current = Locale.forLanguageTag("en")
        assertEquals("Thursday, 24 September", LocalDate.of(2026, 9, 24).formatDay())
        assertEquals("5 May 2026", LocalDate.of(2026, 5, 5).formatDate())
    }
}
