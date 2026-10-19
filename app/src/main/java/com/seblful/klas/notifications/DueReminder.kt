package com.seblful.klas.notifications

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Everything a reminder does when it comes due, except the firing: decide whether it is still owed,
 * work out who it names, and post it.
 *
 * This lives outside the worker on purpose. A `CoroutineWorker` can only be driven with a `Context`
 * and `WorkerParameters`, so anything inside one is reachable only from a device — which is how the
 * "is this lesson still happening, and whose name goes on it" decision came to have no test at all.
 * Here it is ordinary code over the repositories and the [Notifier] port, so a JVM test drives all
 * of it and [com.seblful.klas.work.ReminderWorker] is left as WorkManager's adapter onto it.
 */
@Singleton
class DueReminder @Inject constructor(
    private val classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    private val homework: HomeworkRepository,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) {
    private val names = LessonNames(students, subjects, groups)

    /**
     * Posts the reminder for [classId] if it is still owed at [now], and reports whether it did.
     * A class that has since been deleted, cancelled, completed or moved is simply not announced.
     * [now] defaults to the injected clock, so the shipped path is the one a test can move.
     */
    suspend fun post(classId: Long, now: Instant = clock.now()): Boolean {
        val klass = classes.getById(classId) ?: return false
        val settings = settings.get()
        val lead = settings.reminderLeadMinutes.minutes
        if (!Reminders.shouldPost(klass, settings.notificationsEnabled, now, lead)) return false

        val who = names.who(klass)
        // Read now rather than when the reminder was planned, which is what lets homework be set and ticked
        // without re-planning anything (`HomeworkRepository`): the reminder names what is current as it fires.
        val attached = classes.getSessionFor(klass.id)
            ?.let { homework.currentAttachedTo(it) }
            .orEmpty()
            .joinToString("; ") { it.text }
            .ifEmpty { null }
        notifier.showClassReminder(klass, who, names.subject(klass), attached)
        return true
    }
}
