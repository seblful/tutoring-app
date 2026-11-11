package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.notifications.TrayNavigation.Destination
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** What every notification does, which both trays draw from here. */
class TrayNoticesTest {

    private val notices = TrayNotices(TrayText(Texts.of("en"), "en", hour12 = false))
    private val day = LocalDate(2026, 6, 20)
    private val klass = TutoringClass(
        id = 7, studentId = 1, subjectId = 1, start = Instant.parse("2026-06-20T12:00:00Z"), durationMinutes = 60,
    )

    @Test
    fun `a reminder shows its lesson — and offers to mark it taught or move it`() {
        val notice = notices.reminder(klass, "Anna", "Algebra", homeworkToCheck = null)

        assertEquals(TrayKind.REMINDER, notice.kind)
        assertEquals(7L, notice.classId)
        assertEquals(Destination.LessonOnSchedule(7), notice.tap)
        assertEquals(
            listOf(TrayButton.Complete(7), TrayButton.Open(Strings.class_move, Destination.Lesson(7))),
            notice.buttons,
        )
    }

    @Test
    fun `the digests open the day they are about — and have no buttons`() {
        val none = DaySummary(lessons = 0, minutes = 0, money = Money.ZERO, cancelled = 0)
        val agenda = notices.agenda(Digests.Agenda(day, none, emptyList()), Currency.USD)
        val unfinished = notices.unfinished(Digests.Unfinished(day, emptyList()))
        val upkeep = notices.autoCompleted(2, Money(5000), day, Currency.USD)

        listOf(agenda, unfinished, upkeep).forEach { notice ->
            assertEquals(Destination.Day(day), notice.tap, notice.kind.name)
            assertTrue(notice.buttons.isEmpty(), notice.kind.name)
            assertNull(notice.classId, notice.kind.name)
        }
        assertEquals(
            listOf(TrayKind.AGENDA, TrayKind.UNFINISHED, TrayKind.UPKEEP),
            listOf(agenda, unfinished, upkeep).map { it.kind },
        )
    }

    @Test
    fun `the debt digest opens the debts`() {
        val notice = notices.debts(Digests.Debts(students = 2, total = Money(9000)), Currency.USD)

        assertEquals(TrayKind.DEBTS, notice.kind)
        assertEquals(Destination.Debts, notice.tap)
    }
}
