package com.seblful.repetit.data.repository

import com.seblful.repetit.calendar.CalendarEvent
import com.seblful.repetit.calendar.CalendarSync
import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.GroupDao
import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.local.SubjectDao
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.CalendarSyncPolicy
import com.seblful.repetit.domain.ClassRules
import com.seblful.repetit.domain.ClassSession
import com.seblful.repetit.domain.GroupRules
import com.seblful.repetit.domain.ReminderScheduler
import com.seblful.repetit.domain.SessionEdit
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.TutoringClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassRepository @Inject constructor(
    private val dao: ClassDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val groupDao: GroupDao,
    private val calendarSync: CalendarSync,
    private val reminderScheduler: ReminderScheduler,
    private val settings: SettingsRepository,
) {
    fun observeById(id: Long): Flow<TutoringClass?> = dao.observeById(id).map { it?.toDomain() }

    fun observeAll(): Flow<List<TutoringClass>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<TutoringClass>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    fun observeBetween(from: Instant, to: Instant): Flow<List<TutoringClass>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): TutoringClass? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<TutoringClass> = dao.getAll().map { it.toDomain() }

    /**
     * The whole lesson [classId] belongs to: its group's classes at that start time, or just the one
     * class when it is individual. Editing a group lesson starts here, so the form always works from
     * every member's class rather than from whichever one the tutor happened to tap.
     */
    suspend fun getSessionFor(classId: Long): ClassSession? {
        val klass = dao.getById(classId)?.toDomain() ?: return null
        val groupId = klass.groupId ?: return ClassSession(null, listOf(klass))
        val siblings = dao.getSession(groupId, klass.start.toEpochMilli()).map { it.toDomain() }
        return ClassSession(groupId, siblings.ifEmpty { listOf(klass) })
    }

    /**
     * Re-mirrors every class to the device calendar. Run after the user turns calendar sync on, to
     * backfill events for classes created while it was off. Best-effort, like all calendar work.
     */
    suspend fun resyncCalendar() {
        if (!syncEnabled()) return
        dao.getAll().forEach { mirrorToCalendar(it.toDomain()) }
    }

    /** Removes every mirrored event. Run when the user turns calendar sync off. */
    suspend fun clearCalendar() {
        dao.getAll().forEach { entity ->
            entity.calendarEventId?.let {
                calendarSync.delete(it)
                dao.update(entity.copy(calendarEventId = null))
            }
        }
    }

    suspend fun add(klass: TutoringClass): Long {
        val id = dao.upsert(klass.toEntity())
        mirrorToCalendar(klass.copy(id = id))
        reminderScheduler.refreshNow()
        return id
    }

    /**
     * Persists edits to an existing class (its status/charge already resolved by the caller via
     * [ClassRules]), then mirrors the change to the calendar and refreshes reminders. Distinct from
     * [add] so an edit is unmistakably an in-place update of one row — never an accidental insert
     * that would duplicate the class and double-count its income.
     */
    suspend fun update(klass: TutoringClass) {
        dao.update(klass.toEntity())
        mirrorToCalendar(klass)
        reminderScheduler.refreshNow()
    }

    /**
     * Reschedules a class. Moving it to the future re-plans it (scheduled, charge voided); moving
     * it to the past keeps its status. The calendar event and reminders follow the new state.
     */
    suspend fun move(classId: Long, newStart: Instant, now: Instant = Instant.now()): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val moved = ClassRules.move(current, newStart, now)
        dao.update(moved.toEntity())
        mirrorToCalendar(moved)
        reminderScheduler.refreshNow()
        return moved
    }

    /** Applies a status change, capturing/voiding the charge per [ClassRules]. */
    suspend fun setStatus(classId: Long, to: ClassStatus): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val updated = ClassRules.applyStatus(current, to)
        dao.update(updated.toEntity())
        mirrorToCalendar(updated)
        reminderScheduler.refreshNow()
        return updated
    }

    suspend fun delete(klass: TutoringClass) {
        klass.calendarEventId?.let { calendarSync.delete(it) }
        dao.delete(klass.toEntity())
        reminderScheduler.refreshNow()
    }

    // ---- Group lessons -------------------------------------------------------------------------
    // A group lesson is n classes, one per member, sharing a group id and a start. Every write below
    // is the batch form of a single-class write: the same rules run per class (so each member is
    // billed at their own rate and no charge goes stale), while the calendar is mirrored per class
    // and reminders are refreshed **once** at the end instead of n times.

    /**
     * Books one lesson for a group: a class per entry in [pricesByStudent], each at that member's own
     * hourly rate. Returns the new class ids. Refuses an empty roster — a lesson with nobody in it
     * would be an invisible row that bills no one.
     */
    suspend fun addGroupSession(
        groupId: Long,
        subjectId: Long,
        start: Instant,
        durationMinutes: Int,
        mode: ClassMode,
        pricesByStudent: Map<Long, Money>,
    ): List<Long> {
        require(pricesByStudent.isNotEmpty()) { "A group lesson needs at least one member" }
        val ids = pricesByStudent.map { (studentId, price) ->
            val klass = TutoringClass(
                studentId = studentId,
                subjectId = subjectId,
                start = start,
                durationMinutes = durationMinutes,
                price = price,
                mode = mode,
                groupId = groupId,
            )
            val id = dao.upsert(klass.toEntity())
            mirrorToCalendar(klass.copy(id = id))
            id
        }
        reminderScheduler.refreshNow()
        return ids
    }

    /**
     * Applies one edit to every class of a group lesson. Each member's class goes through
     * [ClassRules.edit] with that member's rate, so the move-and-rebill obligation is honoured per
     * class — moving the lesson into the future re-plans all of them, and a completed lesson's
     * recognized income follows the edit for every member at once.
     */
    suspend fun updateGroupSession(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant = Instant.now(),
    ) {
        session.classes.forEach { c ->
            val resolved = ClassRules.edit(c, GroupRules.editFor(c, changes), newStart, now)
            dao.update(resolved.toEntity())
            mirrorToCalendar(resolved)
        }
        reminderScheduler.refreshNow()
    }

    /**
     * Applies a status change to a whole lesson, skipping the classes it isn't legal for — so
     * "mark completed" on a group lesson whose absentee is already cancelled completes the rest and
     * leaves that correction standing. Returns the classes actually changed.
     */
    suspend fun setStatusForSession(session: ClassSession, to: ClassStatus): List<TutoringClass> {
        val updated = GroupRules.applicableTo(session, to).map { c ->
            val next = ClassRules.applyStatus(c, to)
            dao.update(next.toEntity())
            mirrorToCalendar(next)
            next
        }
        reminderScheduler.refreshNow()
        return updated
    }

    /** Reschedules every class of a lesson together, keeping the members in one session. */
    suspend fun moveSession(
        session: ClassSession,
        newStart: Instant,
        now: Instant = Instant.now(),
    ): List<TutoringClass> {
        val moved = session.classes.map { c ->
            val next = ClassRules.move(c, newStart, now)
            dao.update(next.toEntity())
            mirrorToCalendar(next)
            next
        }
        reminderScheduler.refreshNow()
        return moved
    }

    /** Deletes every class of a lesson, with its calendar events. */
    suspend fun deleteSession(session: ClassSession) {
        session.classes.forEach { c ->
            c.calendarEventId?.let { calendarSync.delete(it) }
            dao.delete(c.toEntity())
        }
        reminderScheduler.refreshNow()
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilli())?.toDomain()

    /**
     * Auto-completes every scheduled class whose end time has passed, billing its booked price.
     * Returns the classes that were completed. (Completed classes need no calendar change — their
     * event is already in the past.)
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> =
        dao.dueForCompletion(now.toEpochMilli())
            .map { it.toDomain() }
            .filter { ClassRules.shouldAutoComplete(it, now) }
            .map { c ->
                val completed = ClassRules.applyStatus(c, ClassStatus.COMPLETED)
                dao.update(completed.toEntity())
                completed
            }

    private suspend fun syncEnabled(): Boolean = settings.get().calendarSyncEnabled

    /**
     * Reflects [klass]'s status into the device calendar. Best-effort: no-ops when calendar sync is
     * off or the calendar is unavailable, so the local database is never blocked by sync.
     */
    private suspend fun mirrorToCalendar(klass: TutoringClass) {
        val s = settings.get()
        if (!s.calendarSyncEnabled) return
        when (CalendarSyncPolicy.actionFor(klass.status)) {
            CalendarSyncPolicy.Action.UPSERT -> {
                val eventId = calendarSync.upsert(klass, titleFor(klass), guestEmailFor(klass), s.reminderLeadMinutes)
                if (eventId != null && eventId != klass.calendarEventId) {
                    dao.update(klass.copy(calendarEventId = eventId).toEntity())
                }
            }
            CalendarSyncPolicy.Action.DELETE -> {
                klass.calendarEventId?.let {
                    calendarSync.delete(it)
                    dao.update(klass.copy(calendarEventId = null).toEntity())
                }
            }
            CalendarSyncPolicy.Action.NONE -> Unit
        }
    }

    /**
     * A group lesson mirrors as one event **per member** — each student keeps their own invite and
     * email reminder, and per-member corrections (one absentee cancelled) stay expressible. The
     * group's name goes in the title so those overlapping events read as one lesson.
     */
    private suspend fun titleFor(klass: TutoringClass): String {
        val student = studentDao.getById(klass.studentId)?.name.orEmpty()
        val subject = subjectDao.getById(klass.subjectId)?.name.orEmpty()
        val group = klass.groupId?.let { groupDao.getById(it)?.name }
        return CalendarEvent.title(student, subject, group)
    }

    /**
     * The student's email, if set — added to the event as a guest so the calendar account (e.g.
     * Google) emails them, including an email reminder an hour before. Blank when unset.
     */
    private suspend fun guestEmailFor(klass: TutoringClass): String? =
        studentDao.getById(klass.studentId)?.email?.takeIf { it.isNotBlank() }
}
