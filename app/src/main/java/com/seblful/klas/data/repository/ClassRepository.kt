package com.seblful.klas.data.repository

import com.seblful.klas.data.local.AttendanceDao
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.LessonDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.toAttendanceEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toLessonEntity
import com.seblful.klas.domain.ClassRules
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.RosterChange
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a lesson write settled on: [Written], carrying what the caller asked for, or [HourTaken] —
 * the hour is already another lesson's, and nothing was written.
 *
 * One tutor cannot teach two lessons at once, and this is the invariant's home: every write that
 * would (re)occupy time makes the check itself, so no way into an hour — booking, editing, moving,
 * or «вернуть в расписание» — depends on its caller remembering to ask first. The callers used to
 * remember for it, three of them, each with its own copy of the ignoring set, and the way in that
 * was forgotten shipped as a double booking. The clash comes back as **data**: wording the refusal
 * is the screen's job, but what it names is the lesson in the way.
 */
sealed interface LessonWrite<out T> {

    /** The rows went in; [value] is what the write was asked for. */
    data class Written<T>(val value: T) : LessonWrite<T>

    /** Refused: [clash] already holds the hour. Nothing was written. */
    data class HourTaken(val clash: ClassSession) : LessonWrite<Nothing>

    /**
     * The written value, insisting the hour was free — for a caller that has already made a clash
     * impossible (a fixture booking into an empty hour), never for the app, which words the refusal.
     */
    fun written(): T = when (this) {
        is Written -> value
        is HourTaken -> error("The hour at ${clash.first.start} is already taken")
    }
}

@Singleton
class ClassRepository @Inject constructor(
    private val dao: ClassDao,
    private val lessons: LessonDao,
    private val attendance: AttendanceDao,
    private val calendar: CalendarMirror,
    private val reminderScheduler: ReminderScheduler,
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
     * This is a **read**, for saying the clash out loud while the form is still open. The writes
     * below make the same check again themselves and refuse ([LessonWrite.HourTaken]) — the form's
     * live answer can be stale by the time save is tapped, and the invariant cannot hang on it.
     */
    fun observeConflicts(slot: Scheduling.Slot, ignoring: Set<Long> = emptySet()): Flow<List<ClassSession>> {
        val window = Scheduling.windowFor(slot)
        return observeBetween(window.from, window.toExclusive).map { Scheduling.conflicts(slot, it, ignoring) }
    }

    /**
     * [observeConflicts] as a one-shot read — the check every (re)occupying write below makes
     * against the database it is about to write.
     *
     * A `suspend` query rather than `observeConflicts(...).first()`: collecting a Room `Flow` for a
     * single value registers an invalidation observer and opens a channel only to close both again,
     * and this runs behind every save, every move and every un-cancel.
     */
    suspend fun conflictsWith(slot: Scheduling.Slot, ignoring: Set<Long> = emptySet()): List<ClassSession> {
        val window = Scheduling.windowFor(slot)
        return Scheduling.conflicts(slot, getBetween(window.from, window.toExclusive), ignoring)
    }

    /**
     * The lesson already holding the hour at [start] for [minutes], if any — asked by every write
     * that would (re)occupy time, before its transaction (a read that only decides whether to write
     * stays outside it). [ignoring] names the lesson's own classes, which cannot stand in their own
     * way.
     *
     * A non-positive [minutes] answers "nobody" rather than asking: such a row occupies no time by
     * its own arithmetic — `Scheduling.Slot` refuses to exist for it, and [Scheduling.conflicts]
     * skips such rows on the read path for the same reason. A stored row like that should not exist
     * (`BackupIntegrity` refuses a backup carrying one), but a status change on it has to come back
     * as an answer, not a crash.
     */
    private suspend fun holdingHour(start: Instant, minutes: Int, ignoring: Set<Long>): ClassSession? =
        if (minutes <= 0) null
        else conflictsWith(Scheduling.Slot(start, minutes), ignoring).firstOrNull()

    /**
     * The classes starting within [horizon] of [now] — what a reminder plan is derived from, rather
     * than every class ever taught (see `work/Maintenance`).
     *
     * The window runs a millisecond past [horizon] because the two bounds disagree by design:
     * [ClassDao.getBetween] excludes its upper bound, while a reminder horizon includes it
     * ([Reminders.plan] keeps a lesson starting exactly on it). Handing over a superset is the safe
     * direction — `plan` applies the real filter — and this keeps the boundary lesson in it.
     */
    suspend fun startingWithin(now: Instant, horizon: Duration): List<TutoringClass> =
        getBetween(now, now.plus(horizon).plusMillis(1))

    private suspend fun getBetween(from: Instant, to: Instant): List<TutoringClass> =
        dao.getBetween(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }

    suspend fun getById(id: Long): TutoringClass? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<TutoringClass> = dao.getAll().map { it.toDomain() }

    /**
     * The whole lesson [classId] belongs to: its group's classes at that start time, or just the one
     * class when it is individual. Editing a group lesson starts here, so the form always works from
     * every member's class rather than from whichever one the tutor happened to tap.
     */
    suspend fun getSessionFor(classId: Long): ClassSession? {
        val klass = dao.getById(classId)?.toDomain() ?: return null
        val members = dao.getSession(klass.lessonId).map { it.toDomain() }
        return ClassSession(klass.groupId, members.ifEmpty { listOf(klass) })
    }

    // ---- Referenced-by guards ------------------------------------------------------------------
    // "Is anything still taught here?" — asked by whoever is about to remove the thing being pointed
    // at. It is a question about classes, so it is answered here.
    //
    // Three repositories used to ask the database instead, each injecting [ClassDao] for a single
    // `countFor*` and nothing else. That handed the whole class table — including `deleteAll` — to
    // three modules that only wanted a yes or no, and it put the reason for the refusal ("those
    // classes are billed history and must keep referencing this") in the module doing the deleting
    // rather than the module that knows it. It also let the answers drift: each caller wrote its own
    // `> 0`, and a count is not what any of them wanted.

    /** Whether any class is taught on [subjectId] — the guard behind deleting a subject. */
    suspend fun anyForSubject(subjectId: Long): Boolean = lessons.countForSubject(subjectId) > 0

    /**
     * Whether [studentId] has any class for [subjectId] — the guard behind unenrolling them from it.
     * Such classes are billed history, and their charge only reads as a rate while the priced subject
     * it was billed at still exists.
     */
    suspend fun anyForStudentSubject(studentId: Long, subjectId: Long): Boolean =
        attendance.countForStudentSubject(studentId, subjectId) > 0

    /**
     * Whether [groupId] has any lesson — the guard behind deleting a group. `classes.groupId` is
     * `ON DELETE RESTRICT`, so this is the check that turns a foreign-key failure into an offer to
     * archive instead.
     */
    suspend fun anyForGroup(groupId: Long): Boolean = lessons.countForGroup(groupId) > 0

    // ---- Writes --------------------------------------------------------------------------------
    // Every write below takes a **lesson**, never a bare class. A group lesson is n classes sharing a
    // group id and a start; an individual class is the same thing with one member ([ClassSession.of]).
    // Treating them as one shape means there is a single implementation per operation instead of a
    // single-class form and a batch form drifting apart.
    //
    // Every one of them also goes through [write], which is the three phases themselves — so what
    // follows is a statement about that function, not a convention each write below has to remember.

    /**
     * A committed set of rows, and what the world outside the database still owes because of it.
     *
     * [value] is whatever the caller wanted back. [mirror] and [drop] are phase 3 expressed as *data*
     * rather than as two more statements at the end of each write, which is what lets [write] run them
     * in the one order they may happen in.
     */
    private class Committed<T>(
        val value: T,
        val mirror: List<TutoringClass> = emptyList(),
        val drop: List<TutoringClass> = emptyList(),
    )

    /**
     * The three phases of a lesson write, in the one order they may happen in:
     *
     *   1. the rules run (pure, no I/O) — before the call, in the caller;
     *   2. every row the lesson touches is written inside [Transactor.transaction] — [rows];
     *   3. the calendar is mirrored and reminders refreshed, *after* the commit — from [Written].
     *
     * This used to be a comment, and then twelve hand-written stacks of the same three steps. The
     * order is not decoration: a group lesson is n rows, so without (2) a failure or a cancellation
     * halfway through leaves a torn lesson — some members dropped, others still at the old time,
     * joiners missing — and the database has no notion of the lesson those rows were meant to make up.
     * (3) sits outside the transaction because the calendar is a `ContentProvider` across an IPC call,
     * and holding SQLite's write lock across it invites an ANR; it is also simply the right order, the
     * calendar being a best-effort mirror of committed data rather than a participant in the write.
     * Nothing in [Transactor]'s own interface says any of that, which is why a write that quietly
     * omitted (3), or ran it inside (2), looked exactly like one that didn't.
     *
     * Reminders refresh **once** per call, not once per class: one lesson earns one reminder, so a
     * per-class refresh would repeat the same derivation n times.
     *
     * @param refreshesReminders false only where the caller owns what follows — see [autoCompleteDue],
     *   whose caller re-derives the plan itself. Opting out is an argument here rather than a missing
     *   line there, so it has to be justified at the point it is taken.
     */
    private suspend fun <T> write(
        refreshesReminders: Boolean = true,
        rows: suspend () -> Committed<T>,
    ): T {
        val written = transactor.transaction { rows() }
        // Leavers' events go before the survivors are mirrored, mirroring the row order inside (2).
        written.drop.forEach { calendar.drop(it) }
        // An empty list is how a write says it has nothing to mirror; the mirror returns immediately.
        calendar.mirror(written.mirror)
        if (refreshesReminders) reminderScheduler.refreshNow()
        return written.value
    }

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
     * Refuses an hour another lesson already holds — [LessonWrite.HourTaken] names it.
     * Refuses an empty roster: a lesson with nobody in it would be an invisible row that bills no one.
     * Refuses a non-positive length for the reason `Scheduling.Slot` gives — such a lesson would occupy
     * no time, and so would stand in no other lesson's way. The last two throw rather than return,
     * because no form can submit them: they are a caller's bug, not a schedule that moved.
     */
    suspend fun book(
        subjectId: Long,
        start: Instant,
        durationMinutes: Int,
        mode: ClassMode,
        studentIds: Collection<Long>,
        price: Money,
        groupId: Long? = null,
    ): LessonWrite<List<Long>> {
        require(studentIds.isNotEmpty()) { "A lesson needs at least one student" }
        require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
        holdingHour(start, durationMinutes, ignoring = emptySet())?.let { return LessonWrite.HourTaken(it) }
        // Every member's row in one transaction: a group lesson is either wholly booked or not
        // booked, never a partial roster nobody asked for.
        val ids = write {
            // The lesson first, once: its hour, subject and status are one fact, and the ids of the rows
            // below are what tie the students to it.
            val lesson = TutoringClass(
                studentId = 0,
                subjectId = subjectId,
                start = start,
                durationMinutes = durationMinutes,
                price = price,
                mode = mode,
                groupId = groupId,
            )
            val lessonId = lessons.upsert(lesson.toLessonEntity())
            val booked = studentIds.map { studentId ->
                val klass = lesson.copy(lessonId = lessonId, studentId = studentId)
                klass.copy(id = attendance.upsert(klass.toAttendanceEntity()))
            }
            Committed(booked.map { it.id }, mirror = booked)
        }
        return LessonWrite.Written(ids)
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
     *
     * [now] is the caller's clock, required rather than defaulted: whether a move re-plans the lesson
     * is decided against it, and a default read here would make the tested path and the shipped path
     * two different paths.
     */
    suspend fun apply(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant,
        roster: Set<Long>? = null,
    ): LessonWrite<List<TutoringClass>> {
        holdingHour(newStart, changes.durationMinutes, session.classes.map { it.id }.toSet())
            ?.let { return LessonWrite.HourTaken(it) }
        val change = roster?.let { GroupRules.rosterChange(session, it, changes, newStart) } ?: RosterChange()
        val leaving = change.dropped.map { it.id }.toSet()
        return LessonWrite.Written(
            persist(
                // editsFor runs over the whole session, so its refusal to re-attribute a group lesson is
                // decided by what the lesson *is* rather than by whoever happens to be staying in it.
                resolved = GroupRules.editsFor(session, changes)
                    .filterNot { (c, _) -> c.id in leaving }
                    .map { (c, edit) -> ClassRules.edit(c, edit, newStart, now) },
                joining = change.joining,
                // Leavers are dropped inside the same transaction as the edit, which is what stops a
                // member being billed on their way out: the delete and the re-bill cannot half-happen.
                dropped = change.dropped,
            ),
        )
    }

    /**
     * Reschedules a whole lesson, keeping its members together in one session, unless the hour is
     * another lesson's ([LessonWrite.HourTaken]) — the lesson's own classes never stand in their own
     * way. Moving it to the future re-plans each class (scheduled, charge voided); moving it to the
     * past keeps each status. [now] is required for the reason [apply] gives.
     */
    suspend fun move(
        session: ClassSession,
        newStart: Instant,
        now: Instant,
    ): LessonWrite<List<TutoringClass>> {
        holdingHour(newStart, session.first.durationMinutes, session.classes.map { it.id }.toSet())
            ?.let { return LessonWrite.HourTaken(it) }
        return LessonWrite.Written(persist(session.classes.map { ClassRules.move(it, newStart, now) }))
    }

    /**
     * Marks what became of a whole lesson: it happened, it was called off, or it is planned again.
     *
     * One status, one row — the lesson's. It used to be a status per member, so this had to pick out the
     * classes the change was legal for and leave the rest ("mark completed" on a group lesson whose
     * absentee was cancelled), which is how a lesson came to hold several statuses at once. Who was
     * actually in it is [setAttendance]'s, and completing carries every still-expected member to present
     * (see [ClassRules.applyStatus]), so the tutor's one tap still bills the room.
     *
     * Returning a lesson **to the schedule** re-occupies its hour, so it is checked like a booking: a
     * cancelled lesson gives its slot back on purpose ([LessonStatus.occupiesTime]), and by the time it is
     * un-cancelled the hour may be somebody else's. That is the one status change that can be refused —
     * every other transition keeps or frees the time it already had.
     */
    suspend fun setStatus(session: ClassSession, to: LessonStatus): LessonWrite<List<TutoringClass>> {
        if (session.status == to) return LessonWrite.Written(emptyList())
        if (to.occupiesTime && !session.status.occupiesTime) {
            holdingHour(session.first.start, session.first.durationMinutes, session.classes.map { it.id }.toSet())
                ?.let { return LessonWrite.HourTaken(it) }
        }
        return LessonWrite.Written(persist(session.classes.map { ClassRules.applyStatus(it, to) }))
    }

    /**
     * Records whether one student was in one lesson — the per-member correction, and the only write that
     * touches a single row of a lesson rather than the lesson.
     *
     * This is what the absentee of a lesson that went ahead used to need a whole fake session for: their
     * class was *cancelled*, which zeroed their charge, left the lesson itself with no single status, and
     * could not express a no-show that is billed anyway ([Attended.ABSENT_BILLED]). Rebilling is
     * [ClassRules.applyAttendance]'s, so the charge follows the fact.
     */
    suspend fun setAttendance(klass: TutoringClass, attended: Attended): List<TutoringClass> =
        persist(listOf(ClassRules.applyAttendance(klass, attended)))

    /**
     * Deletes every class of a lesson, with its calendar events. Deleting a completed lesson removes
     * its recognized income, which is derived rather than stored, so it simply stops being counted.
     */
    suspend fun delete(session: ClassSession) = write {
        // The lesson row, and its students go with it — `attendance.lessonId` is ON DELETE CASCADE, the
        // lesson being the unit of deletion. Deleting per class was the only way to say this before.
        lessons.deleteById(session.lessonId)
        Committed(Unit, drop = session.classes)
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
     * database ends up with. Mirroring follows the commit, per [write]; a joiner is mirrored with the
     * id its insert returned, so the event id lands back on the right row.
     */
    private suspend fun persist(
        resolved: List<TutoringClass>,
        joining: List<TutoringClass> = emptyList(),
        dropped: List<TutoringClass> = emptyList(),
    ): List<TutoringClass> = write {
        // Leavers go first, so the updates below have nobody left to re-bill on their way out.
        dropped.forEach { attendance.deleteById(it.id) }
        // The lesson's own row **once**, from any of its classes — they carry the same lesson, and a
        // caller cannot hand over classes that do not (`ClassSession`). Writing it per class is what
        // stored a status n times and let the copies drift.
        resolved.firstOrNull()?.let { lessons.update(it.toLessonEntity()) }
        resolved.forEach { attendance.update(it.toAttendanceEntity()) }
        val joined = joining.map { ClassRules.rebill(it) }.map { klass ->
            klass.copy(id = attendance.upsert(klass.toAttendanceEntity()))
        }
        // A lesson nobody is left in is not a lesson. Only reachable through a roster change, and only
        // inside this transaction, so a half-emptied lesson is never something another read can see.
        if (dropped.isNotEmpty()) lessons.deleteEmpty()
        Committed(resolved + joined, mirror = resolved + joined, drop = dropped)
    }

    /**
     * Cancels every class of [studentId]'s that is still [LessonStatus.SCHEDULED], and returns them.
     *
     * This is what archiving a student has to do to the schedule, and it lives here because every side
     * effect of a class change does: the charges are voided, the slots are given back, the calendar
     * events are withdrawn and the reminders are re-planned — once, after the commit, like every other
     * write in this class.
     *
     * **Why cancel rather than leave them.** Archiving is how a student leaves without erasing history,
     * and it removes them from every report (`ReportsViewModel` counts active students only). A
     * scheduled class left behind does not stop being live: the maintenance pass auto-completes it,
     * which bills it, and that income then exists in the database while appearing in no total on any
     * screen. The debt tile the archive dialog warns about would go on growing where nobody could see
     * it. Their reminders would also still fire, and their calendar events still stand.
     *
     * **Why cancel rather than delete.** A cancelled class records a lesson that was planned and did not
     * happen, which is true. Deleting it would say it was never arranged.
     *
     * **Why every scheduled class and not only the future ones.** A past class still marked scheduled is
     * exactly the one the auto-complete pass is about to bill. Archiving says the arrangement is over,
     * so a lesson nobody ever confirmed is not about to become income; a lesson that *did* happen was
     * marked completed, and completed classes are left alone here.
     *
     * A group lesson loses only this student's class — a per-member correction (`ClassSession.of`), so
     * the lesson goes ahead for everyone else.
     */
    suspend fun cancelScheduledFor(studentId: Long): List<TutoringClass> {
        val theirs = dao.scheduledForStudent(studentId).map { it.toDomain() }
        if (theirs.isEmpty()) return emptyList()
        // Their own lessons are called off; a group's is not, because it goes ahead without them. That
        // distinction is what the split makes expressible: before, both were "cancel their class", which
        // left the group lesson holding a cancelled member and no status of its own.
        val (mine, groups) = theirs.partition { it.groupId == null }
        val cancelled = mine.map { ClassRules.applyStatus(it, LessonStatus.CANCELLED) }
        val excused = groups.map { ClassRules.applyAttendance(it, Attended.ABSENT) }
        // One transaction for the lot: a half-archived student would have some lessons called off and
        // others still due to bill, which is the state this exists to rule out.
        return write {
            cancelled.forEach { lessons.update(it.toLessonEntity()) }
            (cancelled + excused).forEach { attendance.update(it.toAttendanceEntity()) }
            Committed(cancelled + excused, mirror = cancelled + excused)
        }
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilli())?.toDomain()

    /**
     * Auto-completes every scheduled class whose end time has passed, billing its booked price.
     * Returns the classes that were completed.
     *
     * **The one write that owes no phase 3, and says so at the call to [write].** Nothing to mirror:
     * completing a class leaves its event alone (`CalendarSyncPolicy`), the lesson being in the past
     * already. Nothing to re-plan either — the caller is `Maintenance`, which runs this *first* and
     * then derives the reminder plan from the result, so refreshing here would derive it twice and
     * throw the first one away.
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> {
        // Asked of the lessons, then read back as classes: a lesson is what finishes, and its members are
        // who it bills. `dueForCompletion` used to return a row per member of the same finished hour.
        val dueLessons = lessons.dueForCompletion(now.toEpochMilli()).map { it.id }
        val due = dueLessons
            .flatMap { lessonId -> dao.getSession(lessonId).map { it.toDomain() } }
            .filter { ClassRules.shouldAutoComplete(it, now) }
            .map { ClassRules.applyStatus(it, LessonStatus.COMPLETED) }
        if (due.isEmpty()) return emptyList()
        // One pass, one transaction: an upkeep run that recognized half of an evening's income and
        // then failed would leave a group lesson billing some of its members and not the rest.
        return write(refreshesReminders = false) {
            due.groupBy { it.lessonId }.forEach { (_, members) ->
                lessons.update(members.first().toLessonEntity())
                members.forEach { attendance.update(it.toAttendanceEntity()) }
            }
            Committed(due)
        }
    }
}
