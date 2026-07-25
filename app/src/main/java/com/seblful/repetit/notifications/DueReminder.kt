package com.seblful.repetit.notifications

import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.Reminders
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything a reminder does when it comes due, except the firing: decide whether it is still owed,
 * work out who it names, and post it.
 *
 * This lives outside the worker on purpose. A `CoroutineWorker` can only be driven with a `Context`
 * and `WorkerParameters`, so anything inside one is reachable only from a device — which is how the
 * "is this lesson still happening, and whose name goes on it" decision came to have no test at all.
 * Here it is ordinary code over the repositories and the [Notifier] port, so a JVM test drives all
 * of it and [com.seblful.repetit.work.ReminderWorker] is left as WorkManager's adapter onto it.
 */
@Singleton
class DueReminder @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
) {
    /**
     * Posts the reminder for [classId] if it is still owed at [now], and reports whether it did.
     * A class that has since been deleted, cancelled, completed or moved is simply not announced.
     */
    suspend fun post(classId: Long, now: Instant = Instant.now()): Boolean {
        val klass = classes.getById(classId) ?: return false
        if (!Reminders.shouldPost(klass, settings.get().notificationsEnabled, now)) return false

        val who = Reminders.announcedBy(
            groupName = klass.groupId?.let { groups.getById(it)?.name },
            studentName = students.getById(klass.studentId)?.name,
        )
        notifier.showClassReminder(klass, who, subjects.getById(klass.subjectId)?.name.orEmpty())
        return true
    }
}
