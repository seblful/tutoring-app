package com.seblful.klas.format

import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WeekStartTest {

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test
    fun `a locale carrying the preference states the day`() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeekOrNull(locale("ru-BY-u-fw-sun")))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeekOrNull(locale("ru-BY-u-fw-mon")))
        assertEquals(DayOfWeek.SATURDAY, firstDayOfWeekOrNull(locale("en-US-u-fw-sat")))
    }

    @Test
    fun `all seven days are reachable and distinct`() {
        val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
            .map { firstDayOfWeekOrNull(locale("ru-u-fw-$it")) }

        assertEquals(DayOfWeek.entries.toSet(), days.toSet())
        assertEquals("the order the table maps them in", DayOfWeek.entries.toList(), days)
    }

    @Test
    fun `a locale with no preference answers nothing`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-BY")))
        assertNull(firstDayOfWeekOrNull(locale("en-US")))
        assertNull(firstDayOfWeekOrNull(Locale.ROOT))
    }

    @Test
    fun `a locale carrying other regional preferences is not read as a week start`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-BY-u-mu-celsius-nu-latn")))
        assertEquals(
            DayOfWeek.SUNDAY,
            firstDayOfWeekOrNull(locale("ru-BY-u-fw-sun-mu-celsius")),
        )
    }

    @Test
    fun `an unrecognised preference answers nothing`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-u-fw-xyz")))
        assertNull(firstDayOfWeekOrNull(locale("ru-u-fw-monday")))
    }
}
