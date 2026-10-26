package com.seblful.klas.domain

import com.seblful.klas.domain.model.WeekStartChoice
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekStartChoiceTest {

    @Test
    fun `the system choice follows whatever the phone says`() {
        assertEquals(DayOfWeek.MONDAY, WeekStartChoice.PHONE.resolve(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.SUNDAY, WeekStartChoice.PHONE.resolve(DayOfWeek.SUNDAY))
    }

    @Test
    fun `a chosen day overrides the phone`() {
        assertEquals(DayOfWeek.SUNDAY, WeekStartChoice.SUNDAY.resolve(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.SATURDAY, WeekStartChoice.SATURDAY.resolve(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.MONDAY, WeekStartChoice.MONDAY.resolve(DayOfWeek.SUNDAY))
    }

    @Test
    fun `an unknown stored value follows the phone`() {
        assertEquals(WeekStartChoice.SUNDAY, WeekStartChoice.parse("SUNDAY"))
        assertEquals(WeekStartChoice.PHONE, WeekStartChoice.parse("FRIDAY"))
        assertEquals(WeekStartChoice.PHONE, WeekStartChoice.parse(null))
    }
}
