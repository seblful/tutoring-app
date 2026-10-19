package com.seblful.klas.notifications

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.LessonStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * What a reminder's own buttons do — the half that acts without opening the app.
 *
 * Beside [DueReminder] and for the same reason: a `BroadcastReceiver` can only be driven from a device,
 * so anything decided inside one is decided where no test can reach it. Here it is ordinary code over
 * the repository and the [Notifier] port, and [ReminderActionReceiver] is the platform's adapter onto
 * it.
 *
 * «Перенести» is not here. A new time has to be picked, which needs a screen, so that button opens the
 * lesson instead of doing anything — see [TrayNavigation].
 */
@SingleIn(AppScope::class)
class ReminderActions @Inject constructor(
    private val classes: ClassRepository,
    private val notifier: Notifier,
) {
    /**
     * Marks the whole lesson [classId] belongs to as taught, and reports whether it did.
     *
     * **The lesson, not the class.** A group reminder names the group and is one notification, so its
     * «Проведено» has to settle the room the same way the schedule's swipe does: one status, and every
     * still-expected member carried to present and billed ([ClassRepository.setStatus]).
     *
     * **Legality is asked the same way the sheet asks it** ([SessionRules.sessionTransitions]), so the
     * button can never do what the app's own menu would refuse. It matters here more than on screen,
     * because a notification is a stale view of the lesson by construction: it can sit in the tray for
     * hours while the lesson is cancelled, deleted, or already completed by hand, and the tutor tapping
     * it is answering the lesson as it was when the reminder arrived. A cancelled lesson is *not*
     * quietly resurrected as a taught one on the strength of an old notification.
     *
     * The reminder leaves the tray either way — a button that visibly does nothing reads as broken, and
     * "nothing to do" and "done" look identical from the outside. Deliberately in a `finally`: a failed
     * read would otherwise leave the reminder there, still offering a tap that had already been made.
     */
    suspend fun complete(classId: Long): Boolean = try {
        val session = classes.getSessionFor(classId)
        when {
            session == null -> false
            LessonStatus.COMPLETED !in SessionRules.sessionTransitions(session) -> false
            else -> classes.setStatus(session, LessonStatus.COMPLETED) is LessonWrite.Written
        }
    } finally {
        notifier.cancelClassReminder(classId)
    }
}
