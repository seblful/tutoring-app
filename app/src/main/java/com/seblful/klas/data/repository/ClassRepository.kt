package com.seblful.klas.data.repository

import com.seblful.klas.calendar.CalendarEvent
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.CalendarSyncPolicy
import com.seblful.klas.domain.ClassRules
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.RosterChange
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    private val transactor: Transactor,
) {
    fun observeById(id: Long): Flow<TutoringClass?> = dao.observeById(id).map { it?.toDomain() }

    fun observeAll(): Flow<List<TutoringClass>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<TutoringClass>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /**
     * Every class booked as part of [groupId] — one *lesson* of a three-member group is three of
     * these, so a caller showing lessons collapses them with [GroupRules.sessions].
     */
    fun observeForGroup(groupId: Long): Flow<List<TutoringClass>> =
        dao.observeForGroup(groupId).map { list -> list.map { it.toDomain() } }

    fun observeBetween(from: Instant, to: Instant): Flow<List<TutoringClass>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    /**
     * The lessons already occupying [slot] — [Scheduling.conflicts] over the classes around it — so a
     * booking form can show the clash as the tutor picks a time. [ignoring] names the classes of the
     * lesson being edited, which must not collide with itself.
     *
     * This is a **read**: refusing the write is the view model's job (like the calendar-permission
     * rule), because a refusal has to name the lesson in the way rather than merely throw.
     */
    fun observeConflicts(slot: Scheduling.Slot, ignoring: Set<Long> = emptySet()): Flow<List<ClassSession>> =
        observeBetween(windowStart(slot), slot.end).map { Scheduling.conflicts(slot, it, ignoring) }

    /** [observeConflicts] as a one-shot read — the check a save makes against the database it writes. */
    suspend fun conflictsWith(slot: Scheduling.Slot, ignoring: Set<Long> = emptySet()): List<ClassSession> =
        observeConflicts(slot, ignoring).first()

    /**
     * How far back the conflict query has to look: the window filters on each class's **start**, so a
     * class that began earlier and is still running when [slot] opens would otherwise be missed. A day
     * of lead-in covers every one of them — a class's times are wall-clock on a single date, so none
     * can span 24 hours (see the cross-midnight limitation in `ClassForm`).
     */
    private fun windowStart(slot: Scheduling.Slot): Instant = slot.start.minus(1, ChronoUnit.DAYS)

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
        dao.getAll().forEach { detachCalendarEvent(it.toDomain()) }
    }

    /**
     * Deletes [klass]'s mirrored event and forgets its id, or does nothing when there isn't one.
     *
     * The two halves belong together: an event id left behind after the event is gone would make
     * the next mirror try to update something that no longer exists. Turning sync off, deleting a
     * lesson and cancelling one all owe exactly this, so they all say it here.
     */
    private suspend fun detachCalendarEvent(klass: TutoringClass) {
        val eventId = klass.calendarEventId ?: return
        calendarSync.delete(eventId)
        dao.update(klass.copy(calendarEventId = null).toEntity())
    }

    // ---- Writes --------------------------------------------------------------------------------
    // Every write below takes a **lesson**, never a bare class. A group lesson is n classes sharing a
    // group id and a start; an individual class is the same thing with one member ([ClassSession.of]).
    // Treating them as one shape means there is a single implementation per operation instead of a
    // single-class form and a batch form drifting apart — and the obligations of changing a lesson
    // (run the rules per class, mirror each to the calendar, refresh reminders **once** for the
    // lesson) are discharged here rather than remembered at each call site.
    //
    // Each of them is also **one transaction**, and every one has the same three phases in the same
    // order:
    //
    //   1. the rules run (pure, no I/O),
    //   2. every row the lesson touches is written inside [Transactor.transaction],
    //   3. the calendar is mirrored and reminders refreshed, *after* the commit.
    //
    // A group lesson is n rows, so without (2) a failure or a cancellation halfway through leaves a
    // torn lesson — some members dropped, others still at the old time, joiners missing — and the
    // database has no notion of the lesson those rows were meant to make up. (3) sits outside the
    // transaction on purpose: the calendar is a ContentProvider across an IPC call, and holding
    // SQLite's write lock across it invites an ANR. It is also the right order, the calendar being a
    // best-effort mirror of committed data rather than a participant in the write.

    /**
     * Books one lesson: a class per entry in [studentIds], every one of them at [price] — the group's
     * hourly rate for a group lesson, the student's own for an individual one. [groupId] names the
     * group and is null for an individual lesson; that, and how many students there are, is the only
     * difference between the two. Returns the new class ids.
     *
     * Taking the shared fields once — the rate included, now that a lesson has exactly one — rather
     * than a list of classes is what makes a lesson whose members disagree about their start, subject
     * or price unrepresentable.
     *
     * Refuses an empty roster: a lesson with nobody in it would be an invisible row that bills no one.
     */
    suspend fun book(
        subjectId: Long,
        start: Instant,
        durationMinutes: Int,
        mode: ClassMode,
        studentIds: Collection<Long>,
        price: Money,
        groupId: Long? = null,
    ): List<Long> {
        require(studentIds.isNotEmpty()) { "A lesson needs at least one student" }
        // Every member's row in one transaction: a group lesson is either wholly booked or not
        // booked, never a partial roster nobody asked for.
        val booked = transactor.transaction {
            studentIds.map { studentId ->
                val klass = TutoringClass(
                    studentId = studentId,
                    subjectId = subjectId,
                    start = start,
                    durationMinutes = durationMinutes,
                    price = price,
                    mode = mode,
                    groupId = groupId,
                )
                klass.copy(id = dao.upsert(klass.toEntity()))
            }
        }
        booked.forEach { mirrorToCalendar(it) }
        reminderScheduler.refreshNow()
        return booked.map { it.id }
    }

    /**
     * Applies one edit to every class of [session], moving the lesson to [newStart].
     *
     * Each class goes through [ClassRules.edit] with the lesson's rate, so the move-and-rebill
     * obligation is honoured per class: shifting a lesson into the future re-plans all of it, and a
     * completed lesson's recognized income follows the edit for every member at once. The rules run
     * *here* — a caller has no way to hand over an already-resolved class, and so no way to persist a
     * charge that no longer matches the price and duration it was billed from.
     *
     * [roster] additionally says who the lesson is for afterwards: members outside it lose their
     * class, members in it without one get one ([GroupRules.rosterChange]). Null leaves the roster
     * exactly as it is, which is all an individual lesson can mean. Changing who was in a lesson and
     * changing the lesson's own fields is **one transaction**, so a save can neither bill a member on
     * their way out nor leave a joiner behind at the old time — and a failure partway through leaves
     * the lesson exactly as it was rather than half-reconciled.
     */
    suspend fun apply(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant = Instant.now(),
        roster: Set<Long>? = null,
    ): List<TutoringClass> {
        val change = roster?.let { GroupRules.rosterChange(session, it, changes, newStart) } ?: RosterChange()
        val leaving = change.dropped.map { it.id }.toSet()
        return persist(
            // editsFor runs over the whole session, so its refusal to re-attribute a group lesson is
            // decided by what the lesson *is* rather than by whoever happens to be staying in it.
            resolved = GroupRules.editsFor(session, changes)
                .filterNot { (c, _) -> c.id in leaving }
                .map { (c, edit) -> ClassRules.edit(c, edit, newStart, now) },
            joining = change.joining,
            // Leavers are dropped inside the same transaction as the edit, which is what stops a
            // member being billed on their way out: the delete and the re-bill cannot half-happen.
            dropped = change.dropped,
        )
    }

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
        transactor.transaction { session.classes.forEach { dao.delete(it.toEntity()) } }
        session.classes.forEach { dropCalendarEvent(it) }
        reminderScheduler.refreshNow()
    }

    /**
     * Deletes the event mirroring a class whose row has **already gone**. No `detachCalendarEvent`
     * here: that nulls the row's event id, and there is no longer a row to null it on.
     */
    private suspend fun dropCalendarEvent(klass: TutoringClass) {
        klass.calendarEventId?.let { calendarSync.delete(it) }
    }

    /**
     * The one place an existing lesson is written: every class in [resolved] is updated **in place**,
     * so an edit can't double-count income; then the whole lesson is mirrored and reminders refreshed
     * **once**. One lesson earns one reminder, so refreshing per class would repeat the same work n
     * times.
     *
     * [joining] are members arriving in the lesson, who have no row to update yet. They are inserted
     * and billed by [ClassRules.rebill] — the same rule the others' charges come from — so joining a
     * completed lesson costs what having been in it costs, and no caller can insert an unbilled class
     * into a lesson that has already earned. [dropped] are members leaving it, whose rows go.
     *
     * All three happen in **one** transaction, so the lesson a caller describes is the lesson the
     * database ends up with. Mirroring follows the commit, per the phases documented above; a joiner
     * is mirrored with the id its insert returned, so the event id lands back on the right row.
     */
    private suspend fun persist(
        resolved: List<TutoringClass>,
        joining: List<TutoringClass> = emptyList(),
        dropped: List<TutoringClass> = emptyList(),
    ): List<TutoringClass> {
        val joined = transactor.transaction {
            // Leavers go first, so the updates below have nobody left to re-bill on their way out.
            dropped.forEach { dao.delete(it.toEntity()) }
            resolved.forEach { dao.update(it.toEntity()) }
            joining.map { ClassRules.rebill(it) }.map { klass ->
                klass.copy(id = dao.upsert(klass.toEntity()))
            }
        }
        dropped.forEach { dropCalendarEvent(it) }
        (resolved + joined).forEach { mirrorToCalendar(it) }
        reminderScheduler.refreshNow()
        return resolved + joined
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilli())?.toDomain()

    /**
     * Auto-completes every scheduled class whose end time has passed, billing its booked price.
     * Returns the classes that were completed. (Completed classes need no calendar change — their
     * event is already in the past.)
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> {
        val due = dao.dueForCompletion(now.toEpochMilli())
            .map { it.toDomain() }
            .filter { ClassRules.shouldAutoComplete(it, now) }
            .map { ClassRules.applyStatus(it, ClassStatus.COMPLETED) }
        // One pass, one transaction: an upkeep run that recognized half of an evening's income and
        // then failed would leave a group lesson billing some of its members and not the rest.
        transactor.transaction { due.forEach { dao.update(it.toEntity()) } }
        return due
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
            CalendarSyncPolicy.Action.DELETE -> detachCalendarEvent(klass)
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
