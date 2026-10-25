package com.seblful.klas.data.lessons

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.toAttendanceEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toLessonEntity
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.lesson.LessonEdit
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * What a lesson write settled on: [Written], carrying what the caller asked for, or [HourTaken] —
 * the hour is already another lesson's, and nothing was written.
 */
sealed interface LessonWrite<out T> {

    /** The rows went in; [value] is what the write was asked for. */
    data class Written<T>(val value: T) : LessonWrite<T>

    /** Refused: [clash] already holds the hour. Nothing was written. */
    data class HourTaken(val clash: ClassSession) : LessonWrite<Nothing>

    /** The lesson was deleted before the write could take it. Nothing was written. */
    data object Gone : LessonWrite<Nothing>

    /**
     * The written value, insisting the hour was free — for a caller that has already made a clash
     * impossible (a fixture booking into an empty hour), never for the app, which words the refusal.
     */
    fun written(): T = when (this) {
        is Written -> value
        is HourTaken -> error("The hour at ${clash.first.start} is already taken")
        Gone -> error("The lesson was deleted")
    }
}

/**
 * What booking a repeating lesson settled on: the occurrences that were written, and the ones that were
 * not because their hour was already somebody's.
 *
 * @param bookedIds the new class ids, over every occurrence — a group's lesson contributes one per member.
 * @param lessons how many **lessons** were written. Not `bookedIds.size`, which counts classes: booking a
 * group of four for a month is four lessons and sixteen classes, and the sentence the tutor reads is
 * about the evenings they will be teaching.
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

@SingleIn(AppScope::class)
class ClassRepository @Inject constructor(
    private val dao: ClassDao,
    private val lessons: LessonDao,
    private val attendance: AttendanceDao,
    private val calendar: CalendarMirror,
    private val reminderScheduler: ReminderScheduler,
    private val transactor: Transactor,
) {
    fun observeAll(): Flow<List<TutoringClass>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<TutoringClass>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /**
     * Every class booked as part of [groupId] — one *lesson* of a three-member group is three of
     * these, so a caller showing lessons collapses them with [SessionRules.sessions].
     */
    fun observeForGroup(groupId: Long): Flow<List<TutoringClass>> =
        dao.observeForGroup(groupId).map { list -> list.map { it.toDomain() } }

    /** [who]'s classes starting after [after] — a group's every member's. */
    fun observeOf(who: Assignee, after: Instant): Flow<List<TutoringClass>> = when (who) {
        is Assignee.Student -> dao.observeForStudentAfter(who.studentId, after.toEpochMilliseconds())
        is Assignee.Group -> dao.observeForGroupAfter(who.groupId, after.toEpochMilliseconds())
    }.map { list -> list.map { it.toDomain() } }

    /** Each student's nearest lessons at [now], keyed by student; one with no lessons has no entry. */
    fun observeStudentSpans(now: Instant): Flow<Map<Long, NearestLessons>> =
        lessons.observeStudentSpans(now.toEpochMilliseconds()).map { it.toNearest() }

    /** Each group's nearest lessons at [now], keyed by group; one with no lessons has no entry. */
    fun observeGroupSpans(now: Instant): Flow<Map<Long, NearestLessons>> =
        lessons.observeGroupSpans(now.toEpochMilliseconds()).map { it.toNearest() }

    /**
     * Up to [limit] students and groups, the one taught most recently by [now] first — see
     * [ClassDao.observeRecentlyTaught].
     */
    fun observeRecentlyTaught(now: Instant, limit: Int): Flow<List<Assignee>> =
        dao.observeRecentlyTaught(now.toEpochMilliseconds(), limit).map { rows ->
            rows.mapNotNull { row ->
                row.groupId?.let(Assignee::Group) ?: row.studentId?.let(Assignee::Student)
            }
        }

    private fun List<LessonSpan>.toNearest(): Map<Long, NearestLessons> = associate { span ->
        span.ownerId to NearestLessons(
            next = span.nextMillis?.let(Instant::fromEpochMilliseconds),
            last = span.lastMillis?.let(Instant::fromEpochMilliseconds),
        )
    }

    fun observeBetween(from: Instant, to: Instant): Flow<List<TutoringClass>> =
        dao.observeBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds())
            .map { list -> list.map { it.toDomain() } }

    /**
     * The lessons occupying [slot], read once against the database each write is about to change.
     */
    suspend fun conflictsWith(slot: Scheduling.Slot, ignoring: Set<Long> = emptySet()): List<ClassSession> {
        val window = Scheduling.windowFor(slot)
        return Scheduling.conflicts(slot, getBetween(window.from, window.toExclusive), ignoring)
    }

    private suspend fun holdingHour(session: ClassSession, start: Instant, minutes: Int): ClassSession? =
        if (minutes <= 0) null
        else conflictsWith(Scheduling.Slot(start, minutes), session.ids).firstOrNull()

    private suspend fun holdingHour(session: ClassSession): ClassSession? =
        holdingHour(session, session.start, session.durationMinutes)

    /**
     * The classes still running at [now] or starting within [horizon] of it — what a reminder plan is
     * derived from, rather than every class ever taught (see `maintenance/Maintenance`).
     */
    suspend fun notEndedWithin(now: Instant, horizon: Duration): List<TutoringClass> =
        dao.notEndedBefore(
            fromMillis = (now - Scheduling.LONGEST_LESSON).toEpochMilliseconds(),
            nowMillis = now.toEpochMilliseconds(),
            toMillis = (now + horizon + 1.milliseconds).toEpochMilliseconds(),
        ).map { it.toDomain() }

    /** The classes starting in [[from], [to]) — the upper bound excluded, as [ClassDao.getBetween]'s. */
    suspend fun getBetween(from: Instant, to: Instant): List<TutoringClass> =
        dao.getBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds()).map { it.toDomain() }

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

    private class Committed<T>(
        val value: T,
        val mirror: List<TutoringClass> = emptyList(),
        val drop: List<TutoringClass> = emptyList(),
        val changed: Boolean = true,
    ) {
        fun <R> mapValue(transform: (T) -> R): Committed<R> = Committed(transform(value), mirror, drop, changed)
    }

    /**
     * The three phases of a lesson write, in the one order they may happen in:
     *
     * @param refreshesReminders false only where the caller owns what follows — see [autoCompleteDue],
     * whose caller re-derives the plan itself. Opting out is an argument here rather than a missing
     * line there, so it has to be justified at the point it is taken.
     */
    private suspend fun <T> write(
        refreshesReminders: Boolean = true,
        holding: Boolean = false,
        rows: suspend () -> Committed<T>,
    ): T {
        val written = transactor.transaction { rows() }
        if (!written.changed) return written.value
        if (holding) {
            // An undoable write leaves the calendar alone until it is settled or undone: a cancellation
            // mirrored at once would delete the event, and its undo re-create it and invite again.
            written.mirror.map { it.lessonId }.distinct().forEach { hold(it) }
        } else {
            // Leavers' events go before the survivors are mirrored, mirroring the row order inside (2).
            written.drop.forEach { calendar.drop(it) }
            // Deleted lessons can move homework onto a surviving lesson's invitation.
            calendar.mirror(written.mirror, removed = written.drop)
        }
        if (refreshesReminders) reminderScheduler.refreshNow()
        return written.value
    }

    // ---- Undo ----------------------------------------------------------------------------------
    // How many undoable writes of each lesson still hold its calendar mirror. The rows are written at
    // once; only the device calendar waits, for [settle] or [undo].
    private val held = mutableMapOf<Long, Int>()
    private val heldLock = Mutex()

    private suspend fun hold(lessonId: Long) = heldLock.withLock { held[lessonId] = (held[lessonId] ?: 0) + 1 }

    /** Releases one hold on [lessonId]; true when it was the last, so the calendar may catch up. */
    private suspend fun release(lessonId: Long): Boolean = heldLock.withLock {
        val left = (held[lessonId] ?: return@withLock false) - 1
        if (left > 0) held[lessonId] = left else held.remove(lessonId)
        left <= 0
    }

    /**
     * The workflow's undo window closed: mirror the lesson as it stands, releasing its hold once.
     */
    internal suspend fun settle(receipt: SessionReceipt) {
        if (!release(receipt.lessonId)) return
        current(receipt.after)?.let { calendar.mirror(it.classes) }
    }

    /** [settle] for every lesson one action changed. */
    internal suspend fun settleAll(receipts: List<SessionReceipt>) = receipts.forEach { settle(it) }

    /**
     * Writes [SessionReceipt.before] back — status, time, attendance and charges together — while the
     * lesson is still [SessionReceipt.after], and its hour is free if it would hold one again.
     */
    internal suspend fun undo(receipt: SessionReceipt): UndoResult = undoAll(listOf(receipt)).single()

    /**
     * [undo] for every lesson one action changed, in one transaction. Each lesson is restored or refused
     * on its own, so one edited since does not keep the rest from coming back. Results are in order.
     */
    internal suspend fun undoAll(receipts: List<SessionReceipt>): List<UndoResult> {
        val results = write {
            val outcomes = receipts.map { restore(it) }
            val restored = outcomes.flatMap { it.second }
            Committed(outcomes.map { it.first }, mirror = restored, changed = restored.isNotEmpty())
        }
        // A refused undo leaves the change standing, so the calendar catches up with it now.
        receipts.zip(results).forEach { (receipt, result) ->
            if (release(receipt.lessonId) && result != UndoResult.Restored) {
                current(receipt.after)?.let { calendar.mirror(it.classes) }
            }
        }
        return results
    }

    /** One receipt's undo inside [undoAll]'s transaction, with the rows it wrote back. */
    private suspend fun restore(receipt: SessionReceipt): Pair<UndoResult, List<TutoringClass>> {
        val current = current(receipt.after)
        // The calendar ids are the ones stored now, never the receipt's: only those name real events.
        val events = current?.classes.orEmpty().associate { it.id to it.calendarEventId }
        val restored = receipt.before.classes.map { it.copy(calendarEventId = events[it.id]) }
        val lesson = restored.first()
        val result = when {
            current == null -> UndoResult.Gone
            current.classes.withoutMirrors() != receipt.after.classes.withoutMirrors() -> UndoResult.ChangedSince
            else -> lesson.takeIf { it.status.occupiesTime }
                ?.let { holdingHour(current, it.start, it.durationMinutes) }
                ?.let { UndoResult.HourTaken(it) }
                ?: UndoResult.Restored
        }
        if (result != UndoResult.Restored) return result to emptyList()
        rewrite(lesson = lesson, classes = restored)
        return result to restored
    }

    private fun List<TutoringClass>.withoutMirrors() = map { it.copy(calendarEventId = null) }

    /**
     * [rows] as one undoable write over the lessons [touched] names: a receipt for each lesson it
     * changed, every one's calendar held.
     */
    private suspend fun undoableAll(
        touched: suspend () -> Collection<Long>,
        rows: suspend () -> List<TutoringClass>,
    ): List<SessionReceipt> = write(holding = true) {
        val before = touched().mapNotNull { lessonById(it) }
        val changed = rows()
        val receipts = before.mapNotNull { was -> lessonById(was.lessonId)?.let { SessionReceipt(was, it) } }
            .filter { it.before != it.after }
        Committed(receipts, mirror = changed, changed = changed.isNotEmpty())
    }

    /**
     * [rows] applied to [session] as an undoable write: the receipt of what the lesson was and became,
     * its calendar held. A null receipt when nothing changed, so there is nothing to undo.
     */
    private suspend fun undoable(
        session: ClassSession,
        rows: suspend (ClassSession) -> Committed<LessonWrite<List<TutoringClass>>>,
    ): LessonWrite<SessionReceipt?> = write<LessonWrite<SessionReceipt?>>(holding = true) {
        val before = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
        val done = rows(before)
        val after = if (done.changed) current(session) else null
        done.mapValue<LessonWrite<SessionReceipt?>> { outcome ->
            when (outcome) {
                is LessonWrite.Written -> LessonWrite.Written(after?.let { SessionReceipt(before, it) })
                is LessonWrite.HourTaken -> LessonWrite.HourTaken(outcome.clash)
                LessonWrite.Gone -> LessonWrite.Gone
            }
        }
    }

    /**
     * Books one lesson: a class per entry in [studentIds], every one of them at [price] — the group's
     * hourly rate for a group lesson, the student's own for an individual one. [groupId] names the
     * group and is null for an individual lesson; that, and how many students there are, is the only
     * difference between the two.
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

    /** Books the same lesson at each of [starts] — the whole of what «Повторять» writes. */
    suspend fun bookSeries(
        subjectId: Long,
        starts: List<Instant>,
        durationMinutes: Int,
        mode: ClassMode,
        studentIds: Collection<Long>,
        price: Money,
        groupId: Long? = null,
    ): SeriesWrite {
        require(studentIds.isNotEmpty()) { "A lesson needs at least one student" }
        require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
        require(starts.isNotEmpty()) { "A booking needs at least one start" }

        val slots = starts.map { Scheduling.Slot(it, durationMinutes) }
        // One read for the whole term rather than one per occurrence — the window is
        // `Scheduling.windowFor`'s to shape, for every slot at once, so the write path and the read path
        // ask the same module which classes a clash scan has to be given.
        val window = Scheduling.windowFor(slots)
        return write {
            val around = getBetween(window.from, window.toExclusive)
            val free = mutableListOf<Scheduling.Slot>()
            val skipped = mutableListOf<SeriesWrite.Skipped>()
            slots.forEach { slot ->
                val clash = Scheduling.conflicts(slot, around).firstOrNull()
                if (clash == null) free += slot else skipped += SeriesWrite.Skipped(slot.start, clash)
            }
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
            Committed(
                SeriesWrite(rows.map { it.id }, lessons = free.size, skipped = skipped),
                mirror = rows,
                changed = rows.isNotEmpty(),
            )
        }
    }

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

    /** Applies one edit to every class of [session], moving the lesson to [newStart]. */
    suspend fun apply(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant,
        roster: Set<Long>? = null,
    ): LessonWrite<List<TutoringClass>> {
        // The rules are one call, and the sequence they compose in is theirs — see [SessionRules.lessonEdit].
        // This used to assemble three of them here and read the lesson's own new fields out of the
        // surviving classes, which is how swapping a roster whole kept the lesson at its old time.
        // Leavers are dropped inside the same transaction as the edit, which is what stops a member being
        // billed on their way out: the delete and the re-bill cannot half-happen.
        return write {
            val current = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
            val edit = SessionRules.lessonEdit(current, changes, newStart, now, roster)
            if (edit.lesson.status.occupiesTime) {
                holdingHour(current, newStart, changes.durationMinutes)
                    ?.let { return@write Committed(LessonWrite.HourTaken(it), changed = false) }
            }
            persistRows(edit)
                .mapValue { LessonWrite.Written(it) }
        }
    }

    /**
     * Reschedules a whole lesson, keeping its members together in one session, unless the hour is
     * another lesson's ([LessonWrite.HourTaken]) — the lesson's own classes never stand in their own
     * way. Moving it to the future re-plans each class (scheduled, charge voided); moving it to the
     * past keeps each status.
     */
    suspend fun move(
        session: ClassSession,
        newStart: Instant,
        now: Instant,
    ): LessonWrite<List<TutoringClass>> = write {
        val current = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
        moveRows(current, newStart, now)
    }

    /** [move], undoably — see [SessionReceipt]. */
    internal suspend fun moveUndoable(
        session: ClassSession,
        newStart: Instant,
        now: Instant,
    ): LessonWrite<SessionReceipt?> =
        undoable(session) { moveRows(it, newStart, now) }

    private suspend fun moveRows(
        current: ClassSession,
        newStart: Instant,
        now: Instant,
    ): Committed<LessonWrite<List<TutoringClass>>> {
        val moved = current.classes.map { ClassRules.move(it, newStart, now) }
        if (moved.first().status.occupiesTime) {
            holdingHour(current, newStart, current.durationMinutes)
                ?.let { return Committed(LessonWrite.HourTaken(it), changed = false) }
        }
        return persistRows(LessonEdit(lesson = moved.first(), updated = moved)).mapValue { LessonWrite.Written(it) }
    }

    /** Marks what became of a whole lesson: it happened, it was called off, or it is planned again. */
    suspend fun setStatus(session: ClassSession, to: LessonStatus): LessonWrite<List<TutoringClass>> {
        if (session.status == to) return LessonWrite.Written(emptyList())
        return write {
            // The status it moves *from* is the database's, not the caller's: a lesson completed and
            // billed since the caller read it must not be "completed" again from its old classes.
            val current = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
            statusRows(current, to)
        }
    }

    /** [setStatus], undoably — see [SessionReceipt]. A null receipt when the lesson was already [to]. */
    internal suspend fun setStatusUndoable(session: ClassSession, to: LessonStatus): LessonWrite<SessionReceipt?> =
        undoable(session) { statusRows(it, to) }

    private suspend fun statusRows(
        current: ClassSession,
        to: LessonStatus,
    ): Committed<LessonWrite<List<TutoringClass>>> {
        val clash = if (to.occupiesTime && !current.status.occupiesTime) holdingHour(current) else null
        return when {
            current.status == to -> Committed(LessonWrite.Written(emptyList()), changed = false)
            clash != null -> Committed(LessonWrite.HourTaken(clash), changed = false)
            else -> {
                val resolved = current.classes.map { ClassRules.applyStatus(it, to) }
                persistRows(LessonEdit(lesson = resolved.first(), updated = resolved))
                    .mapValue { LessonWrite.Written(it) }
            }
        }
    }

    /**
     * Corrects a completion without the cancel/re-plan detour. Undo supplies [unchanged] so a receipt
     * cannot undo later edits. Calendar IDs are mirrors and do not count as edits to the lesson.
     */
    suspend fun correctCompletion(session: ClassSession, unchanged: ClassSession? = null): Boolean = write {
        val current = current(session) ?: return@write Committed(false, changed = false)
        val matches = unchanged == null || current.classes.map { it.copy(calendarEventId = null) } ==
            unchanged.classes.map { it.copy(calendarEventId = null) }
        if (current.status != LessonStatus.COMPLETED || !matches) return@write Committed(false, changed = false)
        val corrected = current.classes.map(ClassRules::correctCompletion)
        persistRows(LessonEdit(lesson = corrected.first(), updated = corrected)).mapValue { true }
    }

    /**
     * Records whether one student was in one lesson — the per-member correction, and the only write that
     * touches a single row of a lesson rather than the lesson.
     */
    suspend fun setAttendance(klass: TutoringClass, attended: Attended): List<TutoringClass> = write {
        attendanceRows(klass, attended)
    }

    /**
     * [setAttendance], undoably — see [SessionReceipt]. The receipt is the whole lesson's, since the lesson
     * is what an undo checks is unchanged; [LessonWrite.Gone] when the class was deleted underneath.
     */
    internal suspend fun setAttendanceUndoable(klass: TutoringClass, attended: Attended): LessonWrite<SessionReceipt?> =
        undoable(ClassSession(klass.groupId, listOf(klass))) { _ ->
            val written = attendanceRows(klass, attended)
            written.mapValue { if (it.isEmpty()) LessonWrite.Gone else LessonWrite.Written(it) }
        }

    private suspend fun attendanceRows(klass: TutoringClass, attended: Attended): Committed<List<TutoringClass>> {
        val current = dao.getById(klass.id)?.toDomain()
            ?: return Committed(emptyList(), changed = false)
        val resolved = ClassRules.applyAttendance(current, attended)
        val changed = resolved != current
        // Attendance is one student's correction, never a rewrite of the shared lesson from a stale sheet.
        if (changed) attendance.update(resolved.toAttendanceEntity())
        return Committed(listOf(resolved), mirror = listOf(resolved), changed = changed)
    }

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

    private suspend fun persistRows(edit: LessonEdit): Committed<List<TutoringClass>> {
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
        return Committed(touched, mirror = touched, drop = edit.dropped)
    }

    /** Settles [studentId]'s scheduled classes for archiving and returns the changed rows. */
    suspend fun cancelScheduledFor(studentId: Long): List<TutoringClass> {
        if (dao.scheduledForStudent(studentId).isEmpty()) return emptyList()
        // One transaction for the lot: a half-archived student would have some lessons called off and
        // others still due to bill, which is the state this exists to rule out. Read inside it, so a
        // lesson completed (or re-priced) in the meantime is not written back from a stale row.
        return write {
            val rows = cancelRowsFor(studentId)
            Committed(rows, mirror = rows)
        }
    }

    /** [cancelScheduledFor], undoably: a receipt per lesson it changed — see [undoAll], [settleAll]. */
    internal suspend fun cancelScheduledForUndoable(studentId: Long): List<SessionReceipt> {
        if (dao.scheduledForStudent(studentId).isEmpty()) return emptyList()
        return undoableAll(
            touched = { dao.scheduledForStudent(studentId).map { it.lessonId }.distinct() },
            rows = { cancelRowsFor(studentId) },
        )
    }

    private suspend fun cancelRowsFor(studentId: Long): List<TutoringClass> {
        val theirs = dao.scheduledForStudent(studentId).map { it.toDomain() }
        // Their own lessons are called off; a group's is not, because it goes ahead without them. That
        // distinction is what the split makes expressible: before, both were "cancel their class",
        // which left the group lesson holding a cancelled member and no status of its own.
        val (mine, groups) = theirs.partition { it.groupId == null }
        val cancelled = mine.map { ClassRules.applyStatus(it, LessonStatus.CANCELLED) }
        val excused = groups.map { ClassRules.applyAttendance(it, Attended.ABSENT) }
        // An individual lesson is its one class, so each cancelled one states its own row.
        cancelled.forEach { rewrite(lesson = it, classes = listOf(it)) }
        rewrite(lesson = null, classes = excused)
        return cancelled + excused
    }

    /**
     * Cancels [groupId]'s scheduled lessons for archiving the group, and returns how many **lessons** it
     * called off. One transaction, read inside it, for the same reasons as [cancelScheduledFor].
     */
    suspend fun cancelScheduledForGroup(groupId: Long): Int {
        if (dao.scheduledForGroup(groupId).isEmpty()) return 0
        return write {
            val rows = cancelRowsForGroup(groupId)
            Committed(rows.distinctBy { it.lessonId }.size, mirror = rows)
        }
    }

    /** [cancelScheduledForGroup], undoably: a receipt per lesson it called off — see [undoAll], [settleAll]. */
    internal suspend fun cancelScheduledForGroupUndoable(groupId: Long): List<SessionReceipt> {
        if (dao.scheduledForGroup(groupId).isEmpty()) return emptyList()
        return undoableAll(
            touched = { dao.scheduledForGroup(groupId).map { it.lessonId }.distinct() },
            rows = { cancelRowsForGroup(groupId) },
        )
    }

    private suspend fun cancelRowsForGroup(groupId: Long): List<TutoringClass> =
        dao.scheduledForGroup(groupId).map { it.toDomain() }.groupBy { it.lessonId }.values.flatMap { classes ->
            classes.map { ClassRules.applyStatus(it, LessonStatus.CANCELLED) }
                .also { rewrite(lesson = it.first(), classes = it) }
        }

    /**
     * How many **lessons** are waiting to be auto-completed at [now] — what [autoCompleteDue] would
     * settle if it ran, whether or not the setting that lets it run is on.
     */
    suspend fun countDueForCompletion(now: Instant): Int =
        lessons.countDueForCompletion(now.toEpochMilliseconds())

    /**
     * Auto-completes every scheduled class whose end time has passed, billing its booked price.
     * Returns the classes that were completed.
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> {
        // Asked of the lessons, then read back as classes: a lesson is what finishes, and its members are
        // who it bills. `dueForCompletion` used to return a row per member of the same finished hour.
        // The cheap question first, outside a transaction, so a pass with nothing due opens none.
        if (lessons.countDueForCompletion(now.toEpochMilliseconds()) == 0) return emptyList()
        // One pass, one transaction: an upkeep run that recognized half of an evening's income and
        // then failed would leave a group lesson billing some of its members and not the rest. The rows
        // are read inside it, so a lesson cancelled or re-priced since the question was asked is written
        // from what it is now, not from what it was.
        return write(refreshesReminders = false) {
            val due = dao.dueForCompletion(now.toEpochMilliseconds()).map { it.toDomain() }
                .filter { ClassRules.shouldAutoComplete(it, now) }
                .map { ClassRules.applyStatus(it, LessonStatus.COMPLETED) }
            SessionRules.sessions(due).forEach { rewrite(lesson = it.first, classes = it.classes) }
            Committed(due, changed = due.isNotEmpty())
        }
    }

    private suspend fun current(session: ClassSession): ClassSession? = lessonById(session.lessonId)

    private suspend fun lessonById(lessonId: Long): ClassSession? {
        val members = dao.getSession(lessonId).map { it.toDomain() }
        return members.firstOrNull()?.let { ClassSession(it.groupId, members) }
    }

    private suspend fun rewrite(lesson: TutoringClass?, classes: List<TutoringClass>) {
        lesson?.let { lessons.update(it.toLessonEntity()) }
        classes.forEach { attendance.update(it.toAttendanceEntity()) }
    }
}
