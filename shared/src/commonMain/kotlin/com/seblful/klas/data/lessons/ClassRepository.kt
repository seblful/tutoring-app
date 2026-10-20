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
    fun observeById(id: Long): Flow<TutoringClass?> = dao.observeById(id).map { it?.toDomain() }

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
     * The lessons already occupying [slot] — [Scheduling.conflicts] over the classes around it — so a
     * booking form can show the clash as the tutor picks a time. [editing] is the lesson **being
     * edited**, which must not be found colliding with itself; null while booking something new.
     */
    fun observeConflicts(slot: Scheduling.Slot, editing: ClassSession? = null): Flow<List<ClassSession>> {
        val window = Scheduling.windowFor(slot)
        val ignoring = editing?.ids.orEmpty()
        return observeBetween(window.from, window.toExclusive).map { Scheduling.conflicts(slot, it, ignoring) }
    }

    /**
     * [observeConflicts] as a one-shot read — the check every (re)occupying write below makes
     * against the database it is about to write.
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
        rows: suspend () -> Committed<T>,
    ): T {
        val written = transactor.transaction { rows() }
        if (!written.changed) return written.value
        // Leavers' events go before the survivors are mirrored, mirroring the row order inside (2).
        written.drop.forEach { calendar.drop(it) }
        // Deleted lessons can move homework onto a surviving lesson's invitation.
        calendar.mirror(written.mirror, removed = written.drop)
        if (refreshesReminders) reminderScheduler.refreshNow()
        return written.value
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

    /** ooks the same lesson at each of [starts] — the whole of what «Повторять» writes. */
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

    /** pplies one edit to every class of [session], moving the lesson to [newStart]. */
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
    ): LessonWrite<List<TutoringClass>> {
        return write {
            val current = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
            val moved = current.classes.map { ClassRules.move(it, newStart, now) }
            if (moved.first().status.occupiesTime) {
                holdingHour(current, newStart, current.durationMinutes)
                    ?.let { return@write Committed(LessonWrite.HourTaken(it), changed = false) }
            }
            persistRows(LessonEdit(lesson = moved.first(), updated = moved)).mapValue { LessonWrite.Written(it) }
        }
    }

    /** arks what became of a whole lesson: it happened, it was called off, or it is planned again. */
    suspend fun setStatus(session: ClassSession, to: LessonStatus): LessonWrite<List<TutoringClass>> {
        if (session.status == to) return LessonWrite.Written(emptyList())
        return write {
            // The status it moves *from* is the database's, not the caller's: a lesson completed and
            // billed since the caller read it must not be "completed" again from its old classes.
            val current = current(session) ?: return@write Committed(LessonWrite.Gone, changed = false)
            if (current.status == to) return@write Committed(LessonWrite.Written(emptyList()), changed = false)
            if (to.occupiesTime && !current.status.occupiesTime) {
                holdingHour(current)?.let { return@write Committed(LessonWrite.HourTaken(it), changed = false) }
            }
            val resolved = current.classes.map { ClassRules.applyStatus(it, to) }
            persistRows(LessonEdit(lesson = resolved.first(), updated = resolved)).mapValue { LessonWrite.Written(it) }
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
        val current = dao.getById(klass.id)?.toDomain()
            ?: return@write Committed(emptyList(), changed = false)
        val resolved = ClassRules.applyAttendance(current, attended)
        if (resolved == current) return@write Committed(listOf(current), changed = false)
        // Attendance is one student's correction, never a rewrite of the shared lesson from a stale sheet.
        attendance.update(resolved.toAttendanceEntity())
        Committed(listOf(resolved), mirror = listOf(resolved))
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

    /** ettles [studentId]'s scheduled classes for archiving and returns the changed rows. */
    suspend fun cancelScheduledFor(studentId: Long): List<TutoringClass> {
        if (dao.scheduledForStudent(studentId).isEmpty()) return emptyList()
        // One transaction for the lot: a half-archived student would have some lessons called off and
        // others still due to bill, which is the state this exists to rule out. Read inside it, so a
        // lesson completed (or re-priced) in the meantime is not written back from a stale row.
        return write {
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
            Committed(cancelled + excused, mirror = cancelled + excused)
        }
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilliseconds())?.toDomain()

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

    private suspend fun current(session: ClassSession): ClassSession? {
        val members = dao.getSession(session.lessonId).map { it.toDomain() }
        return members.firstOrNull()?.let { ClassSession(it.groupId, members) }
    }

    private suspend fun rewrite(lesson: TutoringClass?, classes: List<TutoringClass>) {
        lesson?.let { lessons.update(it.toLessonEntity()) }
        classes.forEach { attendance.update(it.toAttendanceEntity()) }
    }
}
