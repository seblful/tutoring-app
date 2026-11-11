package com.seblful.klas.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MonthNameCaseTest {

    private var previous: String = "und"

    @Before
    fun pinRussian() {
        previous = AppLocale.tag
        AppLocale.tag = "ru"
    }

    @After
    fun restore() {
        AppLocale.tag = previous
    }

    @Test
    fun aDayNamesItsMonthInTheGenitive() {
        assertEquals("Четверг, 24 сентября", LocalDate(2026, 9, 24).formatDay())
    }

    @Test
    fun aWeekNamesItsMonthsInTheGenitive() {
        assertEquals("21 – 27 сентября", formatWeekRange(LocalDate(2026, 9, 21), LocalDate(2026, 9, 27)))
        assertEquals("29 июня – 5 июля", formatWeekRange(LocalDate(2026, 6, 29), LocalDate(2026, 7, 5)))
    }

    @Test
    fun aShortDateNamesItsMonthInTheGenitive() {
        assertEquals("5 мая 2026", LocalDate(2026, 5, 5).formatDate())
    }

    @Test
    fun aMonthOnItsOwnStaysNominative() {
        assertEquals("Сентябрь 2026", formatMonth(YearMonth(2026, 9)))
    }

    @Test
    fun englishMonthsAreNotInflected() {
        AppLocale.tag = "en"
        assertEquals("Thursday, September 24", LocalDate(2026, 9, 24).formatDay())
        assertEquals("May 5, 2026", LocalDate(2026, 5, 5).formatDate())
    }
}
