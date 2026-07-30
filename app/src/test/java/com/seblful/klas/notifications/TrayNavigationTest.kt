package com.seblful.klas.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which intents send the tutor somewhere — the rule «Перенести» and the debt digest both ride in on, and
 * one that has to be right about the intents it is *not* about: the app is started by the launcher far
 * more often than by a notification.
 */
class TrayNavigationTest {

    @Test
    fun `a reminder's «Перенести» names the lesson to open`() {
        assertEquals(
            TrayNavigation.Destination.Lesson(42L),
            TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 42),
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

    @Test
    fun `an ordinary launch goes nowhere in particular`() {
        // The launcher, and a tap on a notification's body — which opens the app and nothing more.
        assertNull(TrayNavigation.destinationFor(null, classId = 0))
        assertNull(TrayNavigation.destinationFor("android.intent.action.MAIN", classId = 0))
    }

    @Test
    fun `an id that did not survive is refused, not opened as lesson zero`() {
        // Zero is the booking form's «new lesson», so a lost extra would silently open an empty form
        // instead of the lesson the tutor tapped about; -1 is the other default a caller might read with.
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = 0))
        assertNull(TrayNavigation.destinationFor(TrayNavigation.ACTION_OPEN_LESSON, classId = -1))
    }

    @Test
    fun `a lesson id with somebody else's action is not ours to act on`() {
        assertNull(TrayNavigation.destinationFor(ReminderActionReceiver.ACTION_COMPLETE, classId = 42))
    }
}
