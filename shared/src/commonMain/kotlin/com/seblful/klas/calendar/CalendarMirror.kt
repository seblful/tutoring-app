package com.seblful.klas.calendar

import com.seblful.klas.data.lessons.AttendanceDao
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectDao
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.lesson.CalendarSyncPolicy
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** verything the device calendar shows, and every table that decides it. */
@SingleIn(AppScope::class)
class CalendarMirror @Inject constructor(
    private val classDao: ClassDao,

    private val attendanceDao: AttendanceDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val groupDao: GroupDao,
    private val calendarSync: CalendarSync,
    private val settings: SettingsRepository,
) {

    /** eflects [classes] into the calendar under one snapshot of the settings. */
    suspend fun mirror(classes: List<TutoringClass>) {
        if (classes.isEmpty()) return
        mirror(classes, settings.get())
    }

    private suspend fun mirror(classes: List<TutoringClass>, settings: AppSettings) {
        if (!settings.calendarSyncEnabled) return
        classes.forEach { mirrorOne(it, settings) }
    }

    /**
     * Re-mirrors every class. Run after the tutor turns calendar sync on, to backfill events for
     * lessons booked while it was off.
     */
    suspend fun resyncAll() {
        val settings = settings.get()
        // Checked before the read, so a resync with sync off doesn't walk the whole history to decide
        // it has nothing to do.
        if (!settings.calendarSyncEnabled) return
        mirror(classDao.getAll().map { it.toDomain() }, settings)
    }

    /**
     * A replace-all restore trusts only bindings read from this database, never ids supplied by a file.
     * Wait for the replacement to succeed before touching anything; its failure must remain visible.
     */
    suspend fun remirrorAfter(replace: suspend () -> Unit) {
        val previous = classDao.withCalendarEvent().map { it.toDomain() }
        replace()
        runCatchingCancellable {
            val restored = classDao.getAll().map { it.toDomain() }.associateBy { it.binding() }
            val kept = mutableSetOf<String>()
            previous.forEach { old ->
                val eventId = old.calendarEventId ?: return@forEach
                val same = restored[old.binding()]
                if (same != null && same.calendarEventId == null && eventId !in kept) {
                    attendanceDao.setCalendarEventId(same.id, eventId)
                    kept += eventId
                }
            }
            previous.mapNotNull { it.calendarEventId }.distinct().filterNot { it in kept }
                .forEach { calendarSync.delete(it) }
            resyncAll()
        }
    }

    private fun TutoringClass.binding() = listOf(id, lessonId, studentId, start)

    /**
     * Removes every mirrored event. Run when the tutor turns calendar sync off. Only the classes that
     * actually carry an event id are read: the rest have nothing to withdraw.
     */
    suspend fun clear() {
        classDao.withCalendarEvent().forEach { detach(it.toDomain()) }
    }

    /** Refreshes the events naming [studentId] — their name titles them and their email is invited. */
    suspend fun refreshForStudent(studentId: Long) = refreshWhere { it.studentId == studentId }

    /** Refreshes the events naming [subjectId], whose name is part of every title. */
    suspend fun refreshForSubject(subjectId: Long) = refreshWhere { it.subjectId == subjectId }

    /** Refreshes the events naming [groupId], whose name is what makes n overlapping events one lesson. */
    suspend fun refreshForGroup(groupId: Long) = refreshWhere { it.groupId == groupId }

    private suspend fun refreshWhere(matches: (TutoringClass) -> Boolean) {
        val settings = settings.get()
        if (!settings.calendarSyncEnabled) return
        mirror(classDao.withCalendarEvent().map { it.toDomain() }.filter(matches), settings)
    }

    /**
     * Deletes the event mirroring a class whose row has **already gone**. Not [detach]: that nulls the
     * row's event id, and there is no longer a row to null it on.
     */
    suspend fun drop(klass: TutoringClass) {
        klass.calendarEventId?.let { calendarSync.delete(it) }
    }

    private suspend fun detach(klass: TutoringClass) {
        val eventId = klass.calendarEventId ?: return
        calendarSync.delete(eventId)
        attendanceDao.setCalendarEventId(klass.id, null)
    }

    private suspend fun mirrorOne(klass: TutoringClass, settings: AppSettings) {
        when (CalendarSyncPolicy.actionFor(klass.status, klass.attended)) {
            CalendarSyncPolicy.Action.UPSERT -> {
                // One read of the student, not two: their name titles the event and their address is
                // what invites them to it, and the title and the guest each used to fetch the row.
                val student = studentDao.getById(klass.studentId)
                val eventId = calendarSync.upsert(
                    klass = klass,
                    title = titleFor(klass, student?.name.orEmpty()),
                    guest = guestFor(student, settings),
                    reminderLeadMinutes = settings.reminderLeadMinutes,
                )
                if (eventId != null && eventId != klass.calendarEventId) {
                    attendanceDao.setCalendarEventId(klass.id, eventId)
                }
            }
            // A lesson already taught: its event is kept true, and none is brought into being. No id
            // can come back, so there is nothing to write to `classes` either — see `CalendarSync.refresh`.
            CalendarSyncPolicy.Action.REFRESH -> calendarSync.refresh(
                klass = klass,
                title = titleFor(klass, studentDao.getById(klass.studentId)?.name.orEmpty()),
            )
            CalendarSyncPolicy.Action.DELETE -> detach(klass)
        }
    }

    private suspend fun titleFor(klass: TutoringClass, studentName: String): String =
        CalendarEvent.title(
            studentName = studentName,
            subjectName = subjectDao.getById(klass.subjectId)?.name.orEmpty(),
            groupName = klass.groupId?.let { groupDao.getById(it)?.name },
        )

    private fun guestFor(student: StudentEntity?, settings: AppSettings): Guest? =
        student?.email
            ?.takeIf { it.isNotBlank() }
            ?.let { Guest(email = it, invited = settings.emailInvitesEnabled) }
}
