package com.seblful.klas.notifications

import com.seblful.klas.domain.model.TutoringClass

/**
 * Posts class reminders. A port, so what a worker *decides* to announce can be asserted without a
 * device: posting is the only part that needs Android, and it sits behind this.
 *
 * Best-effort, like the calendar mirror: an adapter with no notification permission no-ops rather
 * than throwing, so a missing permission never fails a worker.
 */
interface Notifier {

    /** Creates the reminder channel if it doesn't exist. Safe to call repeatedly. */
    fun ensureChannel()

    /**
     * Posts a reminder for [klass].
     *
     * @param whoName who the lesson is with — the student, or the **group** when it is a group
     *   lesson, since one lesson earns one reminder and is announced by whoever is in the room.
     */
    fun showClassReminder(klass: TutoringClass, whoName: String, subjectName: String)

    /**
     * Takes the reminder for [classId] out of the tray.
     *
     * On the port rather than left to the adapter that posts, because it is a *consequence* the app
     * decides: a reminder answered by its own «Проведено» button must not stay sitting there, and
     * whether it was is what [ReminderActions] is tested on. Harmless for a reminder that is not
     * showing.
     */
    fun cancelClassReminder(classId: Long)
}
