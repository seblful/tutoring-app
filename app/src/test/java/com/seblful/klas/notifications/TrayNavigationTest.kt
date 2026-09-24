package com.seblful.klas.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Which intents send the tutor somewhere — the rule every tray entry rides in on, and one that has to be
 * right about the intents it is *not* about: the app is started by the launcher far more often than by a
 * notification.
 */
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

    /** The digest is about every debt at once, so it names no id and needs none. */
    @Test
    fun `the debt digest opens the reports`() {
        assertEquals(
            TrayNavigation.Destination.Debts,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DEBTS, classId = 0),
        )
    }

    /** A digest tapped after midnight is still about the day it was posted for. */
    @Test
    fun `a day entry opens the day it carries`() {
        val day = LocalDate.of(2026, 9, 24)
        assertEquals(
            TrayNavigation.Destination.Day(day),
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_DAY, classId = 0, epochDay = day.toEpochDay()),
        )
    }

    /**
     * The activity is exported — the launcher needs it to be — so the day is not only this app's to write,
     * and `LocalDate.ofEpochDay` throws outside its range. A start the app cannot read is still a start.
     */
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

    /** What an evening digest posted before days were carried still says, and the reminders' summary. */
    @Test
    fun `a today entry opens today`() {
        assertEquals(
            TrayNavigation.Destination.Today,
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_TODAY, classId = 0),
        )
    }

    @Test
    fun `an ordinary launch goes nowhere in particular`() {
        assertNull(TrayNavigation.destinationFor(null, classId = 0))
        assertNull(TrayNavigation.destinationFor("android.intent.action.MAIN", classId = 0))
    }

    @Test
    fun `an id that did not survive is refused, not opened as lesson zero`() {
        // Zero is the booking form's «new lesson», so a lost extra would silently open an empty form
        // instead of the lesson the tutor tapped about; -1 is the other default a caller might read with.
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 0))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = -1))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_SHOW_LESSON, classId = 0))
    }

    @Test
    fun `a lesson id with somebody else's action is not ours to act on`() {
        assertNull(TrayNavigation.destinationFor(ReminderActionReceiver.ACTION_COMPLETE, classId = 42))
    }
}
