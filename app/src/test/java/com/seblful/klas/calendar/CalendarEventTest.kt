package com.seblful.klas.calendar

import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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

    /** An invited student is an attendee of the same event, so what the tutor noted must not reach it. */
    @Test
    fun `the description is the homework and never the note`() {
        val klass = TutoringClass(
            studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60,
            notes = "Не готовился, поговорить с мамой", homework = "§12, упр. 4–7",
        )

        assertEquals("§12, упр. 4–7", CalendarEvent.description(klass))
    }

    /** No homework is a blank description — which is what clears one once the homework is deleted. */
    @Test
    fun `a lesson with a note and no homework has a blank description`() {
        val klass = TutoringClass(
            studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60, notes = "Дроби",
        )

        assertEquals("", CalendarEvent.description(klass))
    }
}
