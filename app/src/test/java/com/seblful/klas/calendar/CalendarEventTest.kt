package com.seblful.klas.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarEventTest {

    @Test
    fun `an individual class is titled student then subject`() {
        assertEquals("Иван — Математика", CalendarEvent.title("Иван", "Математика"))
    }

    @Test
    fun `blank parts are omitted`() {
        assertEquals("Математика", CalendarEvent.title("", "Математика"))
        assertEquals("Иван", CalendarEvent.title("Иван", ""))
    }

    @Test
    fun `a group lesson names the group, so its per-member events read as one lesson`() {
        assertEquals(
            "Иван — Математика (11 «А»)",
            CalendarEvent.title("Иван", "Математика", "11 «А»"),
        )
    }

    @Test
    fun `a blank group name changes nothing`() {
        assertEquals("Иван — Математика", CalendarEvent.title("Иван", "Математика", ""))
        assertEquals("Иван — Математика", CalendarEvent.title("Иван", "Математика", null))
    }
}
