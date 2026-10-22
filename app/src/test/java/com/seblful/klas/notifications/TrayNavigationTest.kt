package com.seblful.klas.notifications

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrayNavigationTest {

    @Test
    fun `a reminder's «Перенести» names the lesson to open`() {
        assertEquals(
            TrayNavigation.Destination.Lesson(42L),
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 42),
        )
    }

    /** The reminder itself opens the lesson where it is acted on — its card's sheet, not its form. */
    @Test
    fun `a tap on a reminder names the lesson to show on the schedule`() {
        assertEquals(
            TrayNavigation.Destination.LessonOnSchedule(42L),
            TrayNavigation.destinationFor(TrayNavigation.ACTION_SHOW_LESSON, classId = 42),
        )
    }

    @Test
    fun `the debt digest opens the reports`() {
        assertEquals(
            TrayNavigation.Destination.Debts,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DEBTS, classId = 0),
        )
    }

    @Test
    fun `a day entry opens the day it carries`() {
        val day = LocalDate(2026, 9, 24)
        assertEquals(
            TrayNavigation.Destination.Day(day),
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DAY, classId = 0, epochDay = day.toEpochDays()),
        )
    }

    @Test
    fun `a day entry with no day, or one no date can be, opens today`() {
        assertEquals(
            TrayNavigation.Destination.Today,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DAY, classId = 0, epochDay = null),
        )
        assertEquals(
            TrayNavigation.Destination.Today,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DAY, classId = 0, epochDay = Long.MAX_VALUE),
        )
    }

    @Test
    fun `a today entry opens today`() {
        assertEquals(
            TrayNavigation.Destination.Today,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_TODAY, classId = 0),
        )
    }

    @Test
    fun `the widgets' shortcuts open an empty booking, People and the homework, and an old one a student form`() {
        assertEquals(
            TrayNavigation.Destination.NewLesson,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_BOOK_LESSON, classId = 0),
        )
        assertEquals(
            TrayNavigation.Destination.NewStudent,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_ADD_STUDENT, classId = 0),
        )
        assertEquals(
            TrayNavigation.Destination.Homework,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_HOMEWORK, classId = 0),
        )
        assertEquals(
            TrayNavigation.Destination.People,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_PEOPLE, classId = 0),
        )
    }

    @Test
    fun `an ordinary launch goes nowhere in particular`() {
        assertNull(TrayNavigation.destinationFor(null, classId = 0))
        assertNull(TrayNavigation.destinationFor("android.intent.action.MAIN", classId = 0))
    }

    @Test
    fun `a relaunch from Recents goes nowhere in particular, whatever intent it replays`() {
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 42, fromHistory = true))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DEBTS, classId = 0, fromHistory = true))
    }

    @Test
    fun `an id that did not survive is refused, not opened as lesson zero`() {
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 0))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = -1))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_SHOW_LESSON, classId = 0))
    }

    @Test
    fun `a lesson id with somebody else's action is not ours to act on`() {
        assertNull(TrayNavigation.destinationFor(ReminderActionReceiver.ACTION_COMPLETE, classId = 42))
    }
}
