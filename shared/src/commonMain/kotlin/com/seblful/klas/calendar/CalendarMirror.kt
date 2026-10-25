package com.seblful.klas.calendar

import com.seblful.klas.data.lessons.AttendanceDao
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectDao
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.lesson.CalendarSyncPolicy
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.queries.homework.HomeworkQuery
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** Everything the device calendar shows, and every table that decides it. */
@SingleIn(AppScope::class)
class CalendarMirror @Inject constructor(
    private val classDao: ClassDao,

    private val attendanceDao: AttendanceDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val groupDao: GroupDao,
    private val calendarSync: CalendarSync,
    private val settings: SettingsRepository,
    private val homeworkQuery: HomeworkQuery,
    private val transactor: Transactor,
    private val clock: WallClock,
) {

    /** Reflects [classes] into the calendar under one snapshot of the settings. */
    suspend fun mirror(classes: List<TutoringClass>, removed: List<TutoringClass> = emptyList()) {
        if (classes.isEmpty() && removed.isEmpty()) return
        mirror(classes, settings.get(), changed = classes + removed)
    }

    private suspend fun mirror(
        classes: List<TutoringClass>,
        settings: AppSettings,
        changed: List<TutoringClass> = emptyList(),
    ) {
        if (!settings.calendarSyncEnabled) return
        val homework = homeworkQuery.forCalendar(classes, changed)
        val pass = Pass(settings)
        try {
            (classes + homework.related).forEach {
                pass.mirrorOne(it, CalendarEvent.description(it, homework.attached[it.lessonId].orEmpty()))
            }
        } finally {
            // Events already created keep their ids even when the pass stops early.
            withContext(NonCancellable) { pass.flush() }
        }
    }

    /**
     * Re-mirrors every class. Run after the tutor turns calendar sync on, to backfill events for
     * lessons booked while it was off, and after a restore.
     */
    suspend fun resyncAll() {
        val settings = settings.get()
        // Checked before the read, so a resync with sync off doesn't walk the whole history to decide
        // it has nothing to do.
        if (!settings.calendarSyncEnabled) return
        mirror(classDao.getAll().map { it.toDomain() }, settings)
    }

    /**
     * Re-mirrors the lessons not yet over — after a change to invitations, the reminder lead or the
     * language. A past event's guest, email reminder and location label have nothing left to do.
     */
    suspend fun resyncUpcoming() {
        val settings = settings.get()
        if (!settings.calendarSyncEnabled) return
        val now = clock.now()
        val upcoming = classDao.notEndedBefore(
            fromMillis = (now - Scheduling.LONGEST_LESSON).toEpochMilliseconds(),
            nowMillis = now.toEpochMilliseconds(),
            toMillis = Long.MAX_VALUE,
        )
        mirror(upcoming.map { it.toDomain() }, settings)
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
            // One transaction, so a restore keeping thousands of events re-runs every open screen once.
            val kept = transactor.transaction {
                val kept = mutableSetOf<String>()
                previous.forEach { old ->
                    val eventId = old.calendarEventId ?: return@forEach
                    val same = restored[old.binding()]
                    if (same != null && same.calendarEventId == null && eventId !in kept) {
                        attendanceDao.setCalendarEventId(same.id, eventId)
                        kept += eventId
                    }
                }
                kept
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
    suspend fun refreshForStudent(studentId: Long) =
        refresh { classDao.withCalendarEventForStudent(studentId).map { it.toDomain() } }

    /** Refreshes the events naming [subjectId], whose name is part of every title. */
    suspend fun refreshForSubject(subjectId: Long) =
        refresh { classDao.withCalendarEventForSubject(subjectId).map { it.toDomain() } }

    /** Refreshes the events naming [groupId], whose name is what makes n overlapping events one lesson. */
    suspend fun refreshForGroup(groupId: Long) =
        refresh { classDao.withCalendarEventForGroup(groupId).map { it.toDomain() } }

    /**
     * Refreshes the events of homework given at [givenAt] — both owners after a reassignment, including
     * the event it has just left. It is on each owner's first lesson after [givenAt], and nowhere else.
     */
    suspend fun refreshHomework(assignees: Set<Assignee>, givenAt: Instant) = refresh {
        homeworkQuery.refreshTargets(assignees, givenAt)
    }

    private suspend fun refresh(read: suspend () -> List<TutoringClass>) {
        val settings = settings.get()
        if (!settings.calendarSyncEnabled) return
        mirror(read(), settings)
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

    /**
     * One mirroring pass: names read once per row rather than per event, and new event ids written in
     * batches, so a resync of a long history is not one autocommit (and one screen refresh) per event.
     */
    private inner class Pass(private val settings: AppSettings) {
        private val students = mutableMapOf<Long, StudentEntity?>()
        private val subjects = mutableMapOf<Long, String>()
        private val groups = mutableMapOf<Long, String?>()
        private val bindings = mutableListOf<Pair<Long, String>>()

        suspend fun mirrorOne(klass: TutoringClass, description: String) {
            when (CalendarSyncPolicy.actionFor(klass.status, klass.attended)) {
                CalendarSyncPolicy.Action.UPSERT -> {
                    val student = student(klass.studentId)
                    val eventId = calendarSync.upsert(
                        klass = klass,
                        title = titleFor(klass, student?.name.orEmpty()),
                        guest = guestFor(student),
                        reminderLeadMinutes = settings.reminderLeadMinutes,
                        description = description,
                    )
                    if (eventId != null && eventId != klass.calendarEventId) {
                        bindings += klass.id to eventId
                        if (bindings.size >= BINDINGS_PER_WRITE) flush()
                    }
                }
                // A lesson already taught: its event is kept true, and none is brought into being — so a
                // class without one has nothing to refresh. See `CalendarSync.refresh`.
                CalendarSyncPolicy.Action.REFRESH -> if (klass.calendarEventId != null) {
                    calendarSync.refresh(
                        klass = klass,
                        title = titleFor(klass, student(klass.studentId)?.name.orEmpty()),
                        description = description,
                    )
                }
                CalendarSyncPolicy.Action.DELETE -> detach(klass)
            }
        }

        suspend fun flush() {
            if (bindings.isEmpty()) return
            val batch = bindings.toList()
            bindings.clear()
            transactor.transaction {
                batch.forEach { (id, eventId) -> attendanceDao.setCalendarEventId(id, eventId) }
            }
        }

        private suspend fun student(id: Long): StudentEntity? = students.getOrPut(id) { studentDao.getById(id) }

        private suspend fun titleFor(klass: TutoringClass, studentName: String): String =
            CalendarEvent.title(
                studentName = studentName,
                subjectName = subjects.getOrPut(klass.subjectId) {
                    subjectDao.getById(klass.subjectId)?.name.orEmpty()
                },
                groupName = klass.groupId?.let { id -> groups.getOrPut(id) { groupDao.getById(id)?.name } },
            )

        private fun guestFor(student: StudentEntity?): Guest? =
            student?.email
                ?.takeIf { it.isNotBlank() }
                ?.let { Guest(email = it, invited = settings.emailInvitesEnabled) }
    }

    private companion object {
        /** Event ids written per transaction: few enough that a pass cut short loses little. */
        const val BINDINGS_PER_WRITE = 50
    }
}
