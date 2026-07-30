package com.seblful.klas.notifications

/**
 * Where a tap in the notification tray puts the tutor — **one rule for every notification the app posts.**
 *
 * It began as `ReminderNavigation`, about the single thing a reminder can ask the app to open: a lesson,
 * because «Перенести» needs a screen a notification cannot draw. The weekly debt digest is the second
 * such ask, and it is not a reminder at all — so the name and the answer are the tray's now rather than
 * one notification kind's. What a *move* is remains unencoded: the tutor is simply put in front of the
 * lesson, where a date and time are changed and where a clash on the new hour is already refused.
 *
 * [destinationFor] is a function of two plain values rather than of an `Intent`, and that is the whole
 * point of it: an `Intent` can only be built on a device, so "which intents mean go somewhere" is exactly
 * the kind of question that ends up decided inside a composable with no test. The app is opened by a
 * launcher intent far more often than by a notification, and answering that one wrongly would yank the
 * tutor onto a lesson every single time they open the app.
 *
 * A **sealed** answer rather than a nullable id per destination, so adding a third tray entry cannot
 * quietly add a third boolean to the composable that reads this: every arm has to be handled there.
 */
object TrayNavigation {

    /**
     * The action strings ride in `PendingIntent`s that may already be sitting in a tray, so their
     * **values** are not free to change even though the object holding them was renamed.
     */
    const val ACTION_OPEN_LESSON = "com.seblful.klas.action.OPEN_LESSON"
    const val ACTION_OPEN_DEBTS = "com.seblful.klas.action.OPEN_DEBTS"
    const val EXTRA_CLASS_ID = "classId"

    /** Somewhere a notification can send the tutor. */
    sealed interface Destination {

        /** The lesson's own form — «Перенести» on a reminder. */
        data class Lesson(val classId: Long) : Destination

        /**
         * The reports tab, which is where debts are listed and where each row opens the student who owes.
         * The digest can say how much is owed in total and nothing at all about by whom, so it hands over
         * to the screen that can.
         */
        data object Debts : Destination
    }

    /**
     * Where this intent asks the app to go, or **null** when it asks for nothing — which covers the
     * launcher, a tap on a notification's body (that opens the app and nothing more), and an intent whose
     * id did not survive.
     *
     * @param classId the extra as read, so a missing one is the caller's default (0 or -1) and is refused
     *   here rather than being navigated to as lesson zero — which the booking form reads as "new lesson".
     */
    fun destinationFor(action: String?, classId: Long): Destination? = when {
        action == ACTION_OPEN_LESSON && classId > 0 -> Destination.Lesson(classId)
        action == ACTION_OPEN_DEBTS -> Destination.Debts
        else -> null
    }
}
