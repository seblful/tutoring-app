package com.seblful.klas.notifications

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.queries.homework.HomeworkQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Everything a reminder does when it comes due, except the firing: decide whether it is still owed,
 * work out who it names, and post it.
 *
 * These decisions are ordinary code over the repositories and the [Notifier] port, not a worker coupled
 * to a `Context`, so a JVM test drives them.
 *
 * [ReminderReceiver] is the current adapter: it receives the alarm and calls [post].
 */
@SingleIn(AppScope::class)
class DueReminder @Inject constructor(
    private val classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    private val homework: HomeworkQuery,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) {
    private val names = LessonNames(students, subjects, groups)

    /**
     * Posts the reminder for [classId] if it is still owed at [now], and reports whether it did.
     * A class that has since been deleted, cancelled, completed or moved is simply not announced.
     * [now] defaults to the injected clock, so the shipped path is the one a test can move. [to] is where it is
     * posted: iOS decides ahead and passes a notifier that shows it at [now].
     */
    suspend fun post(classId: Long, now: Instant = clock.now(), to: Notifier = notifier): Boolean {
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
        to.showClassReminder(klass, who, names.subject(klass), attached)
        return true
    }
}
