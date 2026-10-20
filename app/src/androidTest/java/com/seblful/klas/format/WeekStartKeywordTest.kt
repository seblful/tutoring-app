package com.seblful.klas.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toKotlinDayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.temporal.WeekFields
import java.util.Locale

class WeekStartKeywordTest {

    private fun weekStartOf(tag: String): DayOfWeek =
        WeekFields.of(Locale.forLanguageTag(tag)).firstDayOfWeek.toKotlinDayOfWeek()

    @Test
    fun weekFieldsIgnoresTheFirstDayOfWeekKeyword() {
        assertEquals(
            "`fw` moved the week — this platform used to ignore it, see the KDoc",
            weekStartOf("ru-BY"),
            weekStartOf("ru-BY-u-fw-sun"),
        )
        assertEquals(DayOfWeek.MONDAY, weekStartOf("ru-BY-u-fw-sun"))
    }

    @Test
    fun weekFieldsStillReadsTheRegionsOwnConvention() {
        assertEquals(DayOfWeek.MONDAY, weekStartOf("ru-BY"))
        assertEquals(DayOfWeek.SUNDAY, weekStartOf("en-US"))
    }
}
