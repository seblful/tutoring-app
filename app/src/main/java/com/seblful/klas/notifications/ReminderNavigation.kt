package com.seblful.klas.notifications

/**
 * The one thing a reminder can ask the app to *open*: a lesson, because «Перенести» needs a screen.
 *
 * A notification cannot show a date picker, so that button carries the lesson's id into the activity
 * and the app pushes the lesson's own form — where its date and time are, and where a clash on the new
 * hour is already refused and worded. Nothing about a *move* is encoded here; the tutor is simply put
 * in front of the lesson.
 *
 * [lessonToOpen] is the rule, and it is a function of two plain values rather than of an `Intent`: an
 * `Intent` can only be built on a device, and "which intents open a lesson" is exactly the kind of
 * question that ends up decided inside a composable with no test — the app is opened by a launcher
 * intent far more often than by a reminder, and answering that one wrongly would yank the tutor onto a
 * lesson every time they open the app.
 */
object ReminderNavigation {

    const val ACTION_OPEN_LESSON = "com.seblful.klas.action.OPEN_LESSON"
    const val EXTRA_CLASS_ID = "classId"

    /**
     * The lesson the app was opened for, or **null** when it was not opened for one — which covers the
     * launcher, a tap on the reminder's body (that opens the app, nothing more), and an intent whose id
     * did not survive.
     *
     * @param classId the extra as read, so a missing one is the caller's default (0 or -1) and is
     *   refused here rather than being navigated to as lesson zero — which the booking form reads as
     *   "new lesson".
     */
    fun lessonToOpen(action: String?, classId: Long): Long? =
        if (action == ACTION_OPEN_LESSON && classId > 0) classId else null
}
