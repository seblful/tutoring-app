package com.seblful.klas.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which intents open a lesson — the rule «Перенести» rides in on, and one that has to be right about the
 * intents it is *not* about: the app is started by the launcher far more often than by a reminder.
 */
class ReminderNavigationTest {

    @Test
    fun `a reminder's «Перенести» names the lesson to open`() {
        assertEquals(
            42L,
            ReminderNavigation.lessonToOpen(ReminderNavigation.ACTION_OPEN_LESSON, classId = 42),
        )
    }

    @Test
    fun `an ordinary launch opens no lesson`() {
        // The launcher, and a tap on the reminder's body — which opens the app and nothing more.
        assertNull(ReminderNavigation.lessonToOpen(null, classId = 0))
        assertNull(ReminderNavigation.lessonToOpen("android.intent.action.MAIN", classId = 0))
    }

    @Test
    fun `an id that did not survive is refused, not opened as lesson zero`() {
        // Zero is the booking form's «new lesson», so a lost extra would silently open an empty form
        // instead of the lesson the tutor tapped about; -1 is the other default a caller might read with.
        assertNull(ReminderNavigation.lessonToOpen(ReminderNavigation.ACTION_OPEN_LESSON, classId = 0))
        assertNull(ReminderNavigation.lessonToOpen(ReminderNavigation.ACTION_OPEN_LESSON, classId = -1))
    }

    @Test
    fun `a lesson id with somebody else's action is not ours to act on`() {
        assertNull(ReminderNavigation.lessonToOpen(ReminderActionReceiver.ACTION_COMPLETE, classId = 42))
    }
}
