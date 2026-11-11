package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.text.Texts
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The words both trays print: Android's notifier and iOS's read them from here. */
class TrayTextTest {

    private val english = TrayText(Texts.of("en"), "en", hour12 = false)
    private val start = Instant.parse("2026-06-20T12:00:00Z")
    private val klass = TutoringClass(id = 7, studentId = 1, subjectId = 1, start = start, durationMinutes = 60)

    @Test
    fun `a reminder names the lesson collapsed and the homework only expanded`() {
        val entry = english.reminder(klass, "Anna", "Algebra", homeworkToCheck = "p. 12")

        assertEquals("Lesson coming up", entry.title)
        assertTrue(entry.text.endsWith("Algebra · Anna"), entry.text)
        assertFalse("Homework" in entry.text)
        assertEquals("${entry.text}\nHomework: p. 12", entry.expanded)
    }

    @Test
    fun `a reminder with no homework expands to its own line`() {
        val entry = english.reminder(klass, "Anna", "Algebra", homeworkToCheck = null)

        assertEquals(entry.text, entry.expanded)
    }

    @Test
    fun `the time follows the phone's clock`() {
        val twelve = TrayText(Texts.of("en"), "en", hour12 = true).reminder(klass, "Anna", "Algebra", null)

        assertTrue("AM" in twelve.text || "PM" in twelve.text, twelve.text)
        assertFalse("AM" in english.reminder(klass, "Anna", "Algebra", null).text)
    }

    @Test
    fun `an agenda lists the lessons that fit and counts the rest`() {
        val entry = english.agenda(agenda(TrayContent.MAX_AGENDA_LINES + 2), Currency.USD)

        assertEquals("Today", entry.title)
        assertEquals(TrayContent.MAX_AGENDA_LINES, entry.lines.size)
        assertTrue(entry.lines.first().endsWith("Algebra · Student 0"), entry.lines.first())
        assertEquals("2 more", entry.more)
    }

    @Test
    fun `a short agenda has nothing more to count`() {
        assertNull(english.agenda(agenda(2), Currency.USD).more)
    }

    @Test
    fun `the ruble sign is spelled out for the system's font`() {
        val entry = english.debts(Digests.Debts(students = 2, total = Money.ofMajor(40)), Currency.BYN)

        assertEquals("Debts", entry.title)
        assertFalse(Currency.BYN.symbol in entry.text, entry.text)
        assertTrue("BYN" in entry.text, entry.text)
        assertTrue(entry.text.startsWith("2 students"), entry.text)
    }

    private fun agenda(lessons: Int) = Digests.Agenda(
        day = LocalDate(2026, 6, 20),
        summary = DaySummary(lessons = lessons, minutes = lessons * 60, money = Money.ZERO, cancelled = 0),
        lessons = (0 until lessons).map { Digests.Agenda.Entry(start + it.hours, "Algebra", "Student $it") },
    )
}
