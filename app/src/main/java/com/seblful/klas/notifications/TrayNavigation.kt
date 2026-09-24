package com.seblful.klas.notifications

import java.time.LocalDate

/**
 * Where a tap in the notification tray puts the tutor — **one rule for every notification the app posts.**
 *
 * It began as `ReminderNavigation`, about the single thing a reminder can ask the app to open: a lesson,
 * because «Перенести» needs a screen a notification cannot draw. The weekly debt digest is the second
 * such ask and the evening digest the third, and neither is a reminder at all — so the name and the
 * answer are the tray's now rather than one notification kind's. What a *move* is remains unencoded: the
 * tutor is simply put in front of the lesson, where a date and time are changed and where a clash on the
 * new hour is already refused.
 *
 * **Every entry asks for somewhere.** A reminder, the morning agenda and the auto-completion report used
 * to open the app and nothing more, on whatever tab it had been left on — so a tap on «занятие через час»
 * could land on the reports, and the tutor had to go and find the lesson the tray had just named.
 *
 * [destinationFor] is a function of plain values rather than of an `Intent`, and that is the whole
 * point of it: an `Intent` can only be built on a device, so "which intents mean go somewhere" is exactly
 * the kind of question that ends up decided inside a composable with no test. The app is opened by a
 * launcher intent far more often than by a notification, and answering that one wrongly would yank the
 * tutor onto a lesson every single time they open the app.
 *
 * A **sealed** answer rather than a nullable id per destination, so adding a further tray entry cannot
 * quietly add another boolean to the composable that reads this: every arm has to be handled there.
 */
object TrayNavigation {

    /**
     * The action strings ride in `PendingIntent`s that may already be sitting in a tray, so their
     * **values** are not free to change even though the object holding them was renamed.
     */
    const val ACTION_OPEN_LESSON = "com.seblful.klas.action.OPEN_LESSON"
    const val ACTION_OPEN_DEBTS = "com.seblful.klas.action.OPEN_DEBTS"
    const val ACTION_OPEN_TODAY = "com.seblful.klas.action.OPEN_TODAY"
    const val ACTION_OPEN_DAY = "com.seblful.klas.action.OPEN_DAY"
    const val ACTION_SHOW_LESSON = "com.seblful.klas.action.SHOW_LESSON"

    /** The day an [ACTION_OPEN_DAY] entry is about, as `LocalDate.toEpochDay()`. */
    const val EXTRA_DAY = "day"

    /**
     * The extra every notification intent carries its lesson in — **declared once**.
     *
     * It was declared three times with the same literal, here and in the two receivers, which is the shape
     * a wire format should never have: the value is inside `PendingIntent`s already sitting in people's
     * trays, so it is not free to change, and three copies is three places to fail to not change it.
     */
    const val EXTRA_CLASS_ID = "classId"

    /**
     * The lesson an intent names, or **null** when it names none — the one definition of "is this a real
     * class id", for the activity and both `BroadcastReceiver`s.
     *
     * Row ids are positive `AUTOINCREMENT` values, so anything else is a missing extra arriving as whatever
     * default its reader happened to pass. The three readers had two different rules: the receivers read a
     * `-1L` default and refused `< 0`, so **zero passed both of them**, while this object refused it. Zero
     * is precisely the value the booking form reads as "a new lesson", which is why it is the one to refuse.
     */
    fun lessonIdOrNull(raw: Long): Long? = raw.takeIf { it > 0 }

    /** Somewhere a notification can send the tutor. */
    sealed interface Destination {

        /** The lesson's own form — «Перенести» on a reminder. */
        data class Lesson(val classId: Long) : Destination

        /**
         * The schedule on the day the lesson is on **now**, with that lesson's sheet open — a tap on a
         * reminder itself. The sheet is where the lesson is acted on (its status, the homework the reminder
         * named, a move), and it is what tapping the lesson's card opens; the form is for changing it.
         *
         * An id rather than a day, because a lesson can be moved after its reminder was posted.
         */
        data class LessonOnSchedule(val classId: Long) : Destination

        /**
         * The reports tab, scrolled to its debts — where each row opens the student who owes. The digest
         * can say how much is owed in total and nothing at all about by whom, so it hands over to the
         * screen that can.
         */
        data object Debts : Destination

        /**
         * The schedule, **on today** — where the evening digest's unmarked lessons are, and where the
         * swipe that marks one taught already lives.
         *
         * Today rather than "wherever the schedule was left": the digest is about this day, and landing
         * on last Tuesday because that is the page the tutor last browsed would be an answer to a
         * different question. It is the tab rather than a pushed screen, for [Debts]'s reason — a
         * notification that pushed a screen on top of wherever they were would leave a back arrow and no
         * tab selected.
         *
         * Deliberately **not a lesson**: there are several, and «Проведено» from the tray is a reminder's
         * button about one lesson. Billing a whole evening's lessons from a tray entry that may be hours
         * stale is the one thing this digest must not offer.
         *
         * What the evening digest said before it carried its day, and what the reminders' summary still
         * says — several reminders, all of them about the next hour or so.
         */
        data object Today : Destination

        /**
         * The schedule on [date] — the day a digest is about, or the latest day auto-completion settled.
         * Carried rather than read as today, because a digest tapped after midnight is still about
         * yesterday, and today would be a page with none of its lessons on it.
         */
        data class Day(val date: LocalDate) : Destination
    }

    /**
     * Where this intent asks the app to go, or **null** when it asks for nothing — which covers the
     * launcher and an intent whose id did not survive.
     *
     * @param classId the extra as read, so a missing one is the caller's default (0 or -1) and is refused
     *   here rather than being navigated to as lesson zero — which the booking form reads as "new lesson".
     * @param epochDay [EXTRA_DAY] as read, or null when the intent has none. A day that is missing or out
     *   of `LocalDate`'s range is read as today: the activity is exported, as the launcher's is, so the
     *   value is not only this app's to write, and a number it cannot be a date must not crash the start.
     */
    fun destinationFor(action: String?, classId: Long, epochDay: Long? = null): Destination? = when (action) {
        ACTION_OPEN_LESSON -> lessonIdOrNull(classId)?.let { Destination.Lesson(it) }
        ACTION_SHOW_LESSON -> lessonIdOrNull(classId)?.let { Destination.LessonOnSchedule(it) }
        ACTION_OPEN_DEBTS -> Destination.Debts
        ACTION_OPEN_TODAY -> Destination.Today
        ACTION_OPEN_DAY -> epochDay
            ?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }
            ?.let { Destination.Day(LocalDate.ofEpochDay(it)) }
            ?: Destination.Today
        else -> null
    }
}
