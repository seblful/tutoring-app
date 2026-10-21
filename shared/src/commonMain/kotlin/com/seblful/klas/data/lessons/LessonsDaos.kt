package com.seblful.klas.data.lessons

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.flow.Flow

/**
 * One student's class: their [AttendanceEntity] row with its lesson's facts alongside it — what the app has
 * always meant by "a class", and what [ClassDao] reads.
 *
 * **A query result, not a table or a view.** Writes go to the two tables (`LessonDao`, `AttendanceDao`), the
 * point of the split being that each fact has one home; every read joins them with [CLASS_ROWS].
 *
 * It was a `@DatabaseView` (`classes`) until v10. Room validates a view after every migration and never
 * recreates one, so each step that changed a column it projected had to drop and rebuild it with that
 * version's exact `SELECT` — and got it wrong twice (see `Migrations.MIGRATION_6_7`). A join written into the
 * queries asks nothing of a migration: the schema holds tables only, and a column the join stops reading
 * is simply not read.
 */
data class ClassRow(
    val id: Long = 0,
    val lessonId: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val priceMinor: Long = 0,
    /** The **lesson's** status — see [LessonEntity.status]. */
    val status: LessonStatus,
    /** This student's attendance — see [AttendanceEntity.state]. */
    val state: Attended = Attended.EXPECTED,
    val chargeMinor: Long = 0,
    val groupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
    val calendarEventId: String? = null,
)

/**
 * Every [ClassRow] column, and the join that supplies them — **stated once**, so the queries below differ
 * only in what follows it. Columns in their `WHERE` and `ORDER BY` are qualified (`a.` the student's half,
 * `l.` the lesson's): both tables have an `id`, and an unqualified one is ambiguous.
 *
 * `const`, because `@Query` takes an annotation argument and so needs a compile-time constant.
 */
const val CLASS_ROWS: String =
    "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
        "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
        "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
        "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
        "l.status AS status, l.groupId AS groupId, l.mode AS mode " +
        "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

/**
 * Reads of one student's class — [CLASS_ROWS], `attendance JOIN lessons` (see [ClassRow]).
 *
 * Writes are [LessonDao]'s and [AttendanceDao]'s: a joined read has no rows of its own to write, which is
 * also the schema refusing to let a caller write a lesson's status n times.
 */
@Dao
interface ClassDao {
    @Query("$CLASS_ROWS WHERE a.id = :id")
    fun observeById(id: Long): Flow<ClassRow?>

    @Query("$CLASS_ROWS WHERE a.id = :id")
    suspend fun getById(id: Long): ClassRow?

    @Query("$CLASS_ROWS ORDER BY l.startMillis")
    fun observeAll(): Flow<List<ClassRow>>

    @Query("$CLASS_ROWS WHERE a.studentId = :studentId ORDER BY l.startMillis DESC")
    fun observeForStudent(studentId: Long): Flow<List<ClassRow>>

    /** Every class ever booked as part of [groupId] — the group card's lesson list. */
    @Query("$CLASS_ROWS WHERE l.groupId = :groupId ORDER BY l.startMillis DESC")
    fun observeForGroup(groupId: Long): Flow<List<ClassRow>>

    @Query("$CLASS_ROWS WHERE l.startMillis >= :fromMillis AND l.startMillis < :toMillis ORDER BY l.startMillis")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<ClassRow>>

    /**
     * [observeBetween] as a one-shot read, for the callers that want a window of the schedule **once**
     * rather than for as long as a screen is open: the conflict check a save makes, the reminder plan,
     * homework's reminder text and the two lesson-listing digests.
     *
     * They used to get there the long way round. The conflict check and the digests collected
     * [observeBetween] and took its first value, which registers an invalidation observer and opens a
     * channel per read, only to tear all of it down again; the reminder plan read *every class ever
     * taught* to decide about the next week. A `suspend` query over the window is what they actually
     * asked for.
     */
    @Query("$CLASS_ROWS WHERE l.startMillis >= :fromMillis AND l.startMillis < :toMillis ORDER BY l.startMillis")
    suspend fun getBetween(fromMillis: Long, toMillis: Long): List<ClassRow>

    /**
     * The classes still running at [nowMillis] or starting before [toMillis] — [getBetween] widened back
     * to every lesson that has not yet ended, which is what a reminder plan owes (a lesson in progress
     * still owes the reminder an inexact alarm has not delivered yet).
     *
     * [fromMillis] bounds the start from below — no lesson started earlier can still be running — so the
     * start index is read instead of every past lesson, the end not being indexable.
     */
    @Query(
        "$CLASS_ROWS WHERE l.startMillis >= :fromMillis AND l.startMillis < :toMillis " +
            "AND l.startMillis + l.durationMinutes * 60000 > :nowMillis ORDER BY l.startMillis",
    )
    suspend fun notEndedBefore(fromMillis: Long, nowMillis: Long, toMillis: Long): List<ClassRow>

    /** Every class of the lessons [LessonDao.countDueForCompletion] counts — one read, not one per lesson. */
    @Query(
        "$CLASS_ROWS WHERE l.status = 'SCHEDULED' AND (l.startMillis + l.durationMinutes * 60000) <= :nowMillis " +
            "ORDER BY a.id",
    )
    suspend fun dueForCompletion(nowMillis: Long): List<ClassRow>

    /**
     * The classes that currently mirror a calendar event — the only ones turning sync off has
     * anything to undo. The alternative is reading the whole table and no-opping on most of it.
     */
    @Query("$CLASS_ROWS WHERE a.calendarEventId IS NOT NULL")
    suspend fun withCalendarEvent(): List<ClassRow>

    /** The classes with an event naming [studentId] in its title or as its guest. */
    @Query("$CLASS_ROWS WHERE a.calendarEventId IS NOT NULL AND a.studentId = :studentId")
    suspend fun withCalendarEventForStudent(studentId: Long): List<ClassRow>

    /** The classes with an event naming [subjectId] in its title. */
    @Query("$CLASS_ROWS WHERE a.calendarEventId IS NOT NULL AND l.subjectId = :subjectId")
    suspend fun withCalendarEventForSubject(subjectId: Long): List<ClassRow>

    /** The classes with an event naming [groupId] in its title. */
    @Query("$CLASS_ROWS WHERE a.calendarEventId IS NOT NULL AND l.groupId = :groupId")
    suspend fun withCalendarEventForGroup(groupId: Long): List<ClassRow>

    /** [studentId]'s classes starting after [afterMillis] — every lesson homework given then can be on. */
    @Query("$CLASS_ROWS WHERE a.studentId = :studentId AND l.startMillis > :afterMillis ORDER BY l.startMillis")
    suspend fun forStudentAfter(studentId: Long, afterMillis: Long): List<ClassRow>

    /** [groupId]'s classes starting after [afterMillis], every member's — as [forStudentAfter]. */
    @Query("$CLASS_ROWS WHERE l.groupId = :groupId AND l.startMillis > :afterMillis ORDER BY l.startMillis")
    suspend fun forGroupAfter(groupId: Long, afterMillis: Long): List<ClassRow>

    @Query("$CLASS_ROWS WHERE a.studentId = :studentId AND l.startMillis > :afterMillis ORDER BY l.startMillis")
    fun observeForStudentAfter(studentId: Long, afterMillis: Long): Flow<List<ClassRow>>

    @Query("$CLASS_ROWS WHERE l.groupId = :groupId AND l.startMillis > :afterMillis ORDER BY l.startMillis")
    fun observeForGroupAfter(groupId: Long, afterMillis: Long): Flow<List<ClassRow>>

    /**
     * The classes making up one lesson — its `lessonId`, which is the whole of what makes them one
     * lesson. It used to be `groupId` **and** `startMillis`: a pair no schema could hold together, so a
     * member's class moved on its own left the session by definition, and the lesson quietly became two.
     */
    @Query("$CLASS_ROWS WHERE a.lessonId = :lessonId ORDER BY a.id")
    suspend fun getSession(lessonId: Long): List<ClassRow>

    /**
     * One student's classes in lessons that are still merely *planned* — whatever their time. What
     * archiving them has to settle: a scheduled lesson is one the tutor never confirmed happened, and
     * left behind it would auto-complete into income that no report shows (see
     * `StudentRepository.setArchived`).
     */
    @Query("$CLASS_ROWS WHERE a.studentId = :studentId AND l.status = 'SCHEDULED'")
    suspend fun scheduledForStudent(studentId: Long): List<ClassRow>

    /** The next class in a scheduled lesson starting after [nowMillis], for the reminder. */
    @Query("$CLASS_ROWS WHERE l.status = 'SCHEDULED' AND l.startMillis > :nowMillis ORDER BY l.startMillis LIMIT 1")
    suspend fun nextScheduledAfter(nowMillis: Long): ClassRow?

    /**
     * Who was taught last, most recent first, up to [limit]: each student's last individual lesson over
     * by [nowMillis] and each group's, cancelled ones aside. Exactly one of a row's two ids is set.
     */
    @Query(
        "SELECT a.studentId AS studentId, NULL AS groupId, MAX(l.startMillis) AS lastMillis " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId WHERE l.groupId IS NULL AND $TAUGHT " +
            "GROUP BY a.studentId " +
            "UNION ALL " +
            "SELECT NULL AS studentId, l.groupId AS groupId, MAX(l.startMillis) AS lastMillis " +
            "FROM lessons l WHERE l.groupId IS NOT NULL AND $TAUGHT GROUP BY l.groupId " +
            "ORDER BY lastMillis DESC LIMIT :limit",
    )
    fun observeRecentlyTaught(nowMillis: Long, limit: Int): Flow<List<RecentlyTaught>>

    @Query(CLASS_ROWS)
    suspend fun getAll(): List<ClassRow>
}

/**
 * The lesson itself: its hour, its subject, and whether it happened.
 *
 * Small on purpose. A lesson's facts are written **once** here, where the old table wrote them once per
 * member — which is why a status change to a group lesson is now one row's update rather than *n*, and
 * why nothing has to keep those *n* copies agreeing.
 */
@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getById(id: Long): LessonEntity?

    /** How many lessons still planned end at or before [nowMillis] — due for auto-completion. */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE status = 'SCHEDULED' " +
            "AND (startMillis + durationMinutes * 60000) <= :nowMillis",
    )
    suspend fun countDueForCompletion(nowMillis: Long): Int

    /** How many lessons are taught on [subjectId] — used to block deleting a subject with history. */
    @Query("SELECT COUNT(*) FROM lessons WHERE subjectId = :subjectId")
    suspend fun countForSubject(subjectId: Long): Int

    /** How many lessons belong to [groupId] — used to block deleting a group with history. */
    @Query("SELECT COUNT(*) FROM lessons WHERE groupId = :groupId")
    suspend fun countForGroup(groupId: Long): Int

    /**
     * Whether the tutor has booked anything at all. `LIMIT 1` rather than a count: the only question
     * asked of it is yes-or-no, and it is asked on the way into a write.
     *
     * A whole-table read in the sense that it names no id, but not one in the sense `SnapshotDao` owns —
     * it returns a single scalar and no rows, so nothing here can void an invariant the way a
     * `deleteAll()` on this DAO would. See *Two interfaces onto the same tables*.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM lessons LIMIT 1)")
    suspend fun anyExist(): Boolean

    /** Every student's [LessonSpan] at [nowMillis]; a student with no lessons has no row. */
    @Query(
        "SELECT a.studentId AS ownerId, $SPAN FROM attendance a JOIN lessons l ON l.id = a.lessonId " +
            "GROUP BY a.studentId",
    )
    fun observeStudentSpans(nowMillis: Long): Flow<List<LessonSpan>>

    /** Every group's [LessonSpan] at [nowMillis]; a group with no lessons has no row. */
    @Query("SELECT l.groupId AS ownerId, $SPAN FROM lessons l WHERE l.groupId IS NOT NULL GROUP BY l.groupId")
    fun observeGroupSpans(nowMillis: Long): Flow<List<LessonSpan>>

    @Upsert
    suspend fun upsert(lesson: LessonEntity): Long

    @Update
    suspend fun update(lesson: LessonEntity)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Lessons nobody is in any more. A lesson exists to have students in it, so the last one leaving
     * takes it with them — the alternative is a row holding an hour of the tutor's time that no card can
     * show and no report can count. Called after a roster change, inside its transaction.
     */
    @Query("DELETE FROM lessons WHERE id NOT IN (SELECT lessonId FROM attendance)")
    suspend fun deleteEmpty()
}

/** One student's place in a lesson: their attendance, their rate, their charge, their calendar event. */
@Dao
interface AttendanceDao {
    @Query("UPDATE attendance SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: Long, eventId: String?)

    /**
     * How many lessons a student has on one subject — used to refuse un-enrolling a subject whose
     * lessons (taught ones especially) are billed history that must keep referencing a price.
     */
    @Query(
        "SELECT COUNT(*) FROM attendance a JOIN lessons l ON l.id = a.lessonId " +
            "WHERE a.studentId = :studentId AND l.subjectId = :subjectId",
    )
    suspend fun countForStudentSubject(studentId: Long, subjectId: Long): Int

    @Upsert
    suspend fun upsert(row: AttendanceEntity): Long

    @Update
    suspend fun update(row: AttendanceEntity)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * One student's or group's lessons around a moment: the start of the first not yet over, and of the last
 * already over. Cancelled lessons count for neither; a lesson under way is the next one, not the last.
 */
data class LessonSpan(val ownerId: Long, val nextMillis: Long?, val lastMillis: Long?)

/** A student's or a group's last lesson — see [ClassDao.observeRecentlyTaught]. */
data class RecentlyTaught(val studentId: Long?, val groupId: Long?, val lastMillis: Long)

/** A lesson that happened, or was due to: not cancelled, and over by `:nowMillis`. */
private const val TAUGHT: String =
    "l.status != 'CANCELLED' AND l.startMillis + l.durationMinutes * 60000 <= :nowMillis"

private const val SPAN: String =
    "MIN(CASE WHEN l.status != 'CANCELLED' AND l.startMillis + l.durationMinutes * 60000 > :nowMillis " +
        "THEN l.startMillis END) AS nextMillis, " +
        "MAX(CASE WHEN l.status != 'CANCELLED' AND l.startMillis + l.durationMinutes * 60000 <= :nowMillis " +
        "THEN l.startMillis END) AS lastMillis"
