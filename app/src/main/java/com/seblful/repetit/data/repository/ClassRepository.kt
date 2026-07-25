package com.seblful.repetit.data.repository

import com.seblful.repetit.calendar.CalendarEvent
import com.seblful.repetit.calendar.CalendarSync
import com.seblful.repetit.calendar.Guest
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

    // ---- Writes --------------------------------------------------------------------------------
    // Every write below takes a **lesson**, never a bare class. A group lesson is n classes sharing a
    // group id and a start; an individual class is the same thing with one member ([ClassSession.of]).
    // Treating them as one shape means there is a single implementation per operation instead of a
    // single-class form and a batch form drifting apart — and the obligations of changing a lesson
    // (run the rules per class, mirror each to the calendar, refresh reminders **once** for the
    // lesson) are discharged here rather than remembered at each call site.

    /**
     * Books one lesson: a class per entry in [pricesByStudent], each at that student's own hourly
     * rate. [groupId] names the group for a group lesson and is null for an individual one — that is
     * the only difference between the two. Returns the new class ids.
     *
     * Taking the shared fields once, rather than a list of classes, is what makes a lesson whose
     * members disagree about their start or subject unrepresentable.
     *
     * Refuses an empty roster: a lesson with nobody in it would be an invisible row that bills no one.
     */
    suspend fun book(
        subjectId: Long,
        start: Instant,
        durationMinutes: Int,
        mode: ClassMode,
        pricesByStudent: Map<Long, Money>,
        groupId: Long? = null,
    ): List<Long> {
        require(pricesByStudent.isNotEmpty()) { "A lesson needs at least one student" }
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
     * Applies one edit to every class of [session], moving the lesson to [newStart].
     *
     * Each class goes through [ClassRules.edit] with its own student's rate, so the move-and-rebill
     * obligation is honoured per class: shifting a lesson into the future re-plans all of it, and a
     * completed lesson's recognized income follows the edit for every member at once. The rules run
     * *here* — a caller has no way to hand over an already-resolved class, and so no way to persist a
     * charge that no longer matches the price and duration it was billed from.
     */
    suspend fun apply(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant = Instant.now(),
    ): List<TutoringClass> = persist(
        GroupRules.editsFor(session, changes).map { (c, edit) -> ClassRules.edit(c, edit, newStart, now) },
    )

    /**
     * Reschedules a whole lesson, keeping its members together in one session. Moving it to the
     * future re-plans each class (scheduled, charge voided); moving it to the past keeps each status.
     */
    suspend fun move(
        session: ClassSession,
        newStart: Instant,
        now: Instant = Instant.now(),
    ): List<TutoringClass> = persist(session.classes.map { ClassRules.move(it, newStart, now) })

    /**
     * Applies a status change to a whole lesson, skipping the classes it isn't legal for — so
     * "mark completed" on a group lesson whose absentee is already cancelled completes the rest and
     * leaves that correction standing. Returns the classes actually changed.
     */
    suspend fun setStatus(session: ClassSession, to: ClassStatus): List<TutoringClass> =
        persist(GroupRules.applicableTo(session, to).map { ClassRules.applyStatus(it, to) })

    /**
     * Deletes every class of a lesson, with its calendar events. Deleting a completed lesson removes
     * its recognized income, which is derived rather than stored, so it simply stops being counted.
     */
    suspend fun delete(session: ClassSession) {
        session.classes.forEach { c ->
            c.calendarEventId?.let { calendarSync.delete(it) }
            dao.delete(c.toEntity())
        }
        reminderScheduler.refreshNow()
    }

    /**
     * The one place an existing lesson is written: each class is updated in place — never inserted,
     * so income can't be double-counted — and mirrored, then reminders are refreshed **once**. One
     * lesson earns one reminder, so refreshing per class would repeat the same work n times.
     */
    private suspend fun persist(resolved: List<TutoringClass>): List<TutoringClass> {
        resolved.forEach {
            dao.update(it.toEntity())
            mirrorToCalendar(it)
        }
        reminderScheduler.refreshNow()
        return resolved
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
                val eventId = calendarSync.upsert(klass, titleFor(klass), guestFor(klass, s), s.reminderLeadMinutes)
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
     * The student behind [klass] as a calendar guest: their email, if set, plus whether the tutor
     * wants students invited. Null when the student has no address, since there is then nobody to
     * invite *or* withdraw.
     *
     * The invited flag travels with every mirror rather than being read once at toggle time, so
     * turning invites off and re-syncing withdraws the invitations already sent — the setting
     * governs the tutor's whole calendar, not just the lessons booked after it.
     */
    private suspend fun guestFor(klass: TutoringClass, settings: AppSettings): Guest? =
        studentDao.getById(klass.studentId)?.email
            ?.takeIf { it.isNotBlank() }
            ?.let { Guest(email = it, invited = settings.emailInvitesEnabled) }
}
