package com.seblful.klas.notifications

import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Everything a reminder does when it comes due, except the firing: decide whether it is still owed,
 * work out who it names, and post it.
 *
 * These decisions used to live in a `CoroutineWorker`, coupled to a `Context` and
 * `WorkerParameters`, with no direct test of the posting rules. Here they are ordinary code
 * over the repositories and the [Notifier] port, so a JVM test drives them.
 *
 * [ReminderReceiver] is the current adapter: it receives the alarm and calls [post].
 */
@SingleIn(AppScope::class)
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
