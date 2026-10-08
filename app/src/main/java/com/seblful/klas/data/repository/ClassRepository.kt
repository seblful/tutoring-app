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
import com.seblful.klas.domain.LessonEdit
import com.seblful.klas.domain.ReminderScheduler
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

/**
 * What booking a repeating lesson settled on: the occurrences that were written, and the ones that were
 * not because their hour was already somebody's.
 *
 * **It reports rather than refuses**, which is the difference between this and [LessonWrite]. A single
 * booking has one hour and one answer about it; a term of twelve has twelve, and over three months a
 * holiday or an already-booked evening is the normal case, not the exception. Turning the whole term down
 * because of one Monday would be unusable, and skipping quietly would be worse — the tutor would find out
 * in March. So every free hour is booked and the write **says what it did**, which is the shape
 * `GroupRepository.Saved(id, dropped)` already uses for the same reason: the form reports the write
 * instead of predicting it with a second check of its own.
 *
 * @param bookedIds the new class ids, over every occurrence — a group's lesson contributes one per member.
 * @param lessons how many **lessons** were written. Not `bookedIds.size`, which counts classes: booking a
 *   group of four for a month is four lessons and sixteen classes, and the sentence the tutor reads is
 *   about the evenings they will be teaching.
 * @param skipped the occurrences left out, each naming the lesson that holds the hour.
 */
data class SeriesWrite(
    val bookedIds: List<Long>,
    val lessons: Int,
    val skipped: List<Skipped>,
) {
    /** One occurrence that was not booked: when it would have been, and what stood in the way. */
    data class Skipped(val start: Instant, val clash: ClassSession)

    /** Nothing was written at all — every occurrence's hour was taken. */
    val wroteNothing: Boolean get() = lessons == 0
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
     * booking form can show the clash as the tutor picks a time. [editing] is the lesson **being
     * edited**, which must not be found colliding with itself; null while booking something new.
     *
     * It takes the lesson rather than a set of class ids because that derivation is the invariant, not
     * the caller's arithmetic — see [ClassSession.ids]. Handing over ids let the one caller here restate
     * the fourth copy of a rule whose earlier three shipped as a double booking.
     *
     * This is a **read**, for saying the clash out loud while the form is still open. The writes
     * below make the same check again themselves and refuse ([LessonWrite.HourTaken]) — the form's
     * live answer can be stale by the time save is tapped, and the invariant cannot hang on it.
     */
    fun observeConflicts(slot: Scheduling.Slot, editing: ClassSession? = null): Flow<List<ClassSession>> {
        val window = Scheduling.windowFor(slot)
        val ignoring = editing?.ids.orEmpty()
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
     * The lesson already holding the hour [session] is being written into, if any — asked by every write
     * that would (re)occupy time, before its transaction (a read that only decides whether to write
     * stays outside it).
     *
     * **It derives its own exclusions.** The classes [session] consists of cannot stand in their own way,
     * and that set is [ClassSession.ids]' to state. Every caller used to assemble it by hand alongside a
     * start-and-length pair of its own choosing — three copies here and a fourth in a view model, for one
     * question — so a fifth write could get any of the three arguments wrong and be refused nothing.
     *
     * A non-positive [minutes] answers "nobody" rather than asking: such a row occupies no time by
     * its own arithmetic — `Scheduling.Slot` refuses to exist for it, and [Scheduling.conflicts]
     * skips such rows on the read path for the same reason. A stored row like that should not exist
     * (`BackupIntegrity` refuses a backup carrying one), but a status change on it has to come back
     * as an answer, not a crash.
     */
    private suspend fun holdingHour(session: ClassSession, start: Instant, minutes: Int): ClassSession? =
        if (minutes <= 0) null
        else conflictsWith(Scheduling.Slot(start, minutes), session.ids).firstOrNull()

    /** The hour [session] already holds — for a write that re-occupies its time without moving it. */
    private suspend fun holdingHour(session: ClassSession): ClassSession? =
        holdingHour(session, session.start, session.durationMinutes)

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

    /** The classes starting in [[from], [to]) — the upper bound excluded, as [ClassDao.getBetween]'s. */
    suspend fun getBetween(from: Instant, to: Instant): List<TutoringClass> =
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

    /**
     * Whether the tutor has booked **anything, ever** — asked on the way into a booking, so that the one
     * that finds this false is knowably their first.
     *
     * Not a guard like the three above it: nothing is refused on the strength of this answer. It exists
     * because a lesson is the first moment a *reminder* becomes a real thing about to happen, which is
     * when the app asks to be allowed to show one — see `ClassEditViewModel.save`. Asking earlier means
     * asking before the tutor has met the feature, and asking never is what left the permission denied on
     * every install (`SettingsViewModel.unblockNotifications`).
     */
    suspend fun anyLessons(): Boolean = lessons.anyExist()

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
     *
     * **A run of one**, delegating to [bookSeries] rather than checking the hour and writing the rows a
     * second time. The two had the same body, and the difference between them is only how many answers
     * come back: one hour has one, so a skipped occurrence *is* [LessonWrite.HourTaken]. Keeping both
     * implementations would have left the app booking through one of them and most of the suite through
     * the other — the shape `LessonFilter.accepted` was named away from, with the tested copy and the
     * shipped copy free to drift apart.
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
        val written = bookSeries(
            subjectId = subjectId,
            starts = listOf(start),
            durationMinutes = durationMinutes,
            mode = mode,
            studentIds = studentIds,
            price = price,
            groupId = groupId,
        )
        return written.skipped.firstOrNull()
            ?.let { LessonWrite.HourTaken(it.clash) }
            ?: LessonWrite.Written(written.bookedIds)
    }

    /**
     * Books the same lesson at each of [starts] — the whole of what «Повторять» writes.
     *
     * Every occurrence is an ordinary lesson; what this owns is that they are decided and written
     * **together**, in the three phases [write] states:
     *
     *   1. one read over the union of the occurrences' windows, partitioning them into free and taken
     *      (a read that only *decides* whether to write stays outside the transaction);
     *   2. every free occurrence's rows inside one transaction;
     *   3. one calendar mirror and one reminder refresh for the lot.
     *
     * A loop over [book] would have been the obvious implementation and is the thing to avoid: it would
     * re-derive the whole reminder plan twelve times and hand the calendar twelve separate batches, which
     * is exactly what [write]'s "refresh once per lesson write, not once per class" exists to prevent —
     * only worse, because the unit here is a term rather than a lesson.
     *
     * **The occurrences are checked against the schedule as it stands, and not against each other.** They
     * can't collide: [Recurrence] spaces them a week or more apart and a lesson runs at most 23 h 59 m.
     *
     * Refuses an empty roster and a non-positive length for the reasons [book] gives, and an empty
     * [starts] for the same kind of reason — a booking with no occurrences would write nothing and report
     * success.
     */
    suspend fun bookSeries(
        subjectId: Long,
        starts: List<Instant>,
        durationMinutes: Int,
        mode: ClassMode,
        studentIds: Collection<Long>,
        price: Money,
        groupId: Long? = null,
        notes: String = "",
    ): SeriesWrite {
        require(studentIds.isNotEmpty()) { "A lesson needs at least one student" }
        require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
        require(starts.isNotEmpty()) { "A booking needs at least one start" }

        val slots = starts.map { Scheduling.Slot(it, durationMinutes) }
        // One read for the whole term rather than one per occurrence — the window is
        // `Scheduling.windowFor`'s to shape, for every slot at once, so the write path and the read path
        // ask the same module which classes a clash scan has to be given.
        val window = Scheduling.windowFor(slots)
        val around = getBetween(window.from, window.toExclusive)
        val free = mutableListOf<Scheduling.Slot>()
        val skipped = mutableListOf<SeriesWrite.Skipped>()
        slots.forEach { slot ->
            val clash = Scheduling.conflicts(slot, around).firstOrNull()
            if (clash == null) free += slot else skipped += SeriesWrite.Skipped(slot.start, clash)
        }
        // Returned before [write] rather than through it: with nothing to commit there is nothing to
        // mirror and nothing to re-plan, and running the phases anyway would re-derive the entire
        // reminder plan to arrive back where it started.
        if (free.isEmpty()) return SeriesWrite(emptyList(), lessons = 0, skipped = skipped)

        val booked = write {
            val rows = free.flatMap { slot ->
                insertLesson(
                    subjectId = subjectId,
                    start = slot.start,
                    durationMinutes = durationMinutes,
                    mode = mode,
                    studentIds = studentIds,
                    price = price,
                    groupId = groupId,
                )
            }
            Committed(rows, mirror = rows)
        }
        return SeriesWrite(booked.map { it.id }, lessons = free.size, skipped = skipped)
    }

    /**
     * Writes one lesson's rows — the lesson, then a class per student — and hands back the classes with
     * the ids their inserts returned.
     *
     * **Inside a transaction, always**: it is phase 2 and nothing else, which is why it neither opens one
     * nor mirrors anything. Named rather than inlined so that [bookSeries]'s transaction body reads as
     * the one thing it is — "write each free occurrence's lesson" — with what a lesson's rows *are*
     * stated beside it rather than nested two lambdas deep.
     */
    private suspend fun insertLesson(
        subjectId: Long,
        start: Instant,
        durationMinutes: Int,
        mode: ClassMode,
        studentIds: Collection<Long>,
        price: Money,
        groupId: Long?,
    ): List<TutoringClass> {
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
        return studentIds.map { studentId ->
            val klass = lesson.copy(lessonId = lessonId, studentId = studentId)
            klass.copy(id = attendance.upsert(klass.toAttendanceEntity()))
        }
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
        holdingHour(session, newStart, changes.durationMinutes)
            ?.let { return LessonWrite.HourTaken(it) }
        // The rules are one call, and the sequence they compose in is theirs — see [GroupRules.lessonEdit].
        // This used to assemble three of them here and read the lesson's own new fields out of the
        // surviving classes, which is how swapping a roster whole kept the lesson at its old time.
        // Leavers are dropped inside the same transaction as the edit, which is what stops a member being
        // billed on their way out: the delete and the re-bill cannot half-happen.
        return LessonWrite.Written(persist(GroupRules.lessonEdit(session, changes, newStart, now, roster)))
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
        holdingHour(session, newStart, session.durationMinutes)
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
            holdingHour(session)?.let { return LessonWrite.HourTaken(it) }
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
     * The one place an existing lesson is written: [LessonEdit.lesson] states the lesson's own row and
     * every class in [LessonEdit.updated] is rewritten **in place**, so an edit can't double-count
     * income; then the whole lesson is mirrored and reminders refreshed **once**. One lesson earns one
     * reminder, so refreshing per class would repeat the same work n times.
     *
     * [LessonEdit.joining] are members arriving in the lesson, who have no row to update yet. They are
     * inserted and billed by [ClassRules.rebill] — the same rule the others' charges come from — so
     * joining a completed lesson costs what having been in it costs, and no caller can insert an unbilled
     * class into a lesson that has already earned. [LessonEdit.dropped] are members leaving it, whose
     * rows go.
     *
     * All of it happens in **one** transaction, so the lesson a caller describes is the lesson the
     * database ends up with. Mirroring follows the commit, per [write]; a joiner is mirrored with the
     * id its insert returned, so the event id lands back on the right row.
     */
    private suspend fun persist(edit: LessonEdit): List<TutoringClass> = write {
        // Leavers go first, so the updates below have nobody left to re-bill on their way out.
        edit.dropped.forEach { attendance.deleteById(it.id) }
        // The lesson's own row **once**, and from the edit's own statement of it rather than from
        // whichever class happens to be first in the list — a roster swapped whole leaves that list empty
        // and used to leave the lesson at its old time. Writing it per class is what stored a status n
        // times and let the copies drift.
        rewrite(lesson = edit.lesson, classes = edit.updated)
        val joined = edit.joining.map { ClassRules.rebill(it) }.map { klass ->
            klass.copy(id = attendance.upsert(klass.toAttendanceEntity()))
        }
        // A lesson nobody is left in is not a lesson. Only reachable through a roster change, and only
        // inside this transaction, so a half-emptied lesson is never something another read can see.
        if (edit.dropped.isNotEmpty()) lessons.deleteEmpty()
        val touched = edit.updated + joined
        Committed(touched, mirror = touched, drop = edit.dropped)
    }

    /**
     * [persist] for a write that keeps the lesson's roster exactly as it is — a move, a status change, one
     * member's attendance. Every class of it is being rewritten, so any of them states the lesson's row.
     */
    private suspend fun persist(resolved: List<TutoringClass>): List<TutoringClass> {
        require(resolved.isNotEmpty()) { "A write touches at least one class of the lesson" }
        return persist(LessonEdit(lesson = resolved.first(), updated = resolved))
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
            // An individual lesson is its one class, so each cancelled one states its own row.
            cancelled.forEach { rewrite(lesson = it, classes = listOf(it)) }
            rewrite(lesson = null, classes = excused)
            Committed(cancelled + excused, mirror = cancelled + excused)
        }
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilli())?.toDomain()

    /**
     * How many **lessons** are waiting to be auto-completed at [now] — what [autoCompleteDue] would
     * settle if it ran, whether or not the setting that lets it run is on.
     *
     * Asked before the sweep happens, because the tutor is put the question first (see
     * `SettingsViewModel.setAutoCompleteEnabled`). It reads the same query the sweep reads rather than
     * a count of its own, so the number in the question cannot disagree with what the answer then does.
     *
     * Lessons, not classes: a group's finished hour is one lesson to confirm, not one per member.
     */
    suspend fun countDueForCompletion(now: Instant): Int =
        lessons.dueForCompletion(now.toEpochMilli()).size

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
            GroupRules.sessions(due).forEach { rewrite(lesson = it.first, classes = it.classes) }
            Committed(due)
        }
    }

    /**
     * Rewrites [lesson]'s own row **once** and every one of [classes] in place — the shape every write of
     * an existing lesson takes, inside the caller's [write]. A null [lesson] leaves the lesson row alone,
     * for a per-member correction: excusing one student from a group's lesson changes nothing about the
     * hour, and writing its row from a class read before the transaction could put back an hour another
     * write has since moved.
     */
    private suspend fun rewrite(lesson: TutoringClass?, classes: List<TutoringClass>) {
        lesson?.let { lessons.update(it.toLessonEntity()) }
        classes.forEach { attendance.update(it.toAttendanceEntity()) }
    }
}
