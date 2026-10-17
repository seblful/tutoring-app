package com.seblful.klas.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.flow.Flow

// Rows are returned unordered wherever the order would depend on **name** collation: SQLite's
// `COLLATE NOCASE` folds ASCII only, so it cannot order Russian names, and restating the real rule in
// SQL is not possible. Name ordering is applied once in Kotlin — see `domain/people/NameOrder` — by the
// repositories. Ordering by time or id stays here, where no collation is involved.

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Upsert
    suspend fun upsert(subject: SubjectEntity): Long

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE archived = 0")
    fun observeActive(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE archived = 0")
    suspend fun getActive(): List<StudentEntity>

    @Query("SELECT * FROM students")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: Long): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): StudentEntity?

    @Upsert
    suspend fun upsert(student: StudentEntity): Long

    @Query("SELECT subjectId FROM student_subjects WHERE studentId = :studentId")
    suspend fun getSubjectIds(studentId: Long): List<Long>

    /** The other direction: who is priced for a subject. Drives a subject-bound group's roster. */
    @Query("SELECT studentId FROM student_subjects WHERE subjectId = :subjectId")
    fun observeStudentIdsForSubject(subjectId: Long): Flow<List<Long>>

    @Query("SELECT studentId FROM student_subjects WHERE subjectId = :subjectId")
    suspend fun getStudentIdsForSubject(subjectId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSubjectLink(ref: StudentSubjectCrossRef)

    @Query("DELETE FROM student_subjects WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun removeSubjectLink(studentId: Long, subjectId: Long)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM student_groups WHERE archived = 0")
    fun observeActive(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM student_groups")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM student_groups")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM student_groups WHERE id = :id")
    fun observeById(id: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM student_groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    /**
     * How many groups are taught [subjectId] — what stops a subject being deleted out from under a
     * group. `student_groups.subjectId` is `ON DELETE RESTRICT`, so without this the delete reaches
     * SQLite and throws a constraint violation from inside a `launch`, which is a crash rather than a
     * refusal (see `SubjectRepository.delete`).
     */
    @Query("SELECT COUNT(*) FROM student_groups WHERE subjectId = :subjectId")
    suspend fun countForSubject(subjectId: Long): Int

    @Upsert
    suspend fun upsert(group: GroupEntity): Long

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("SELECT studentId FROM group_members WHERE groupId = :groupId")
    fun observeMemberIds(groupId: Long): Flow<List<Long>>

    @Query("SELECT studentId FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberIds(groupId: Long): List<Long>

    @Query("SELECT * FROM group_members")
    fun observeAllMembers(): Flow<List<GroupMemberCrossRef>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(ref: GroupMemberCrossRef)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND studentId = :studentId")
    suspend fun removeMember(groupId: Long, studentId: Long)
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE studentId = :studentId")
    fun observeForStudent(studentId: Long): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun get(studentId: Long, subjectId: Long): PriceEntity?

    @Upsert
    suspend fun upsert(price: PriceEntity)

    @Query("DELETE FROM prices WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun delete(studentId: Long, subjectId: Long)
}

/**
 * The rate log. Ordered `atMillis DESC` so the newest change reads first — and because SQLite treats
 * NULL as the smallest value, the seeded "this rate predates the log" rows fall to the bottom, which
 * is where a change of unknown date belongs. `id DESC` breaks ties, so two changes recorded in the
 * same millisecond still come out in the order they were made.
 */
@Dao
interface RateHistoryDao {
    @Query("SELECT * FROM price_history WHERE studentId = :studentId ORDER BY atMillis DESC, id DESC")
    fun observeForStudent(studentId: Long): Flow<List<RateChangeEntity>>

    @Query(
        "SELECT * FROM price_history WHERE studentId = :studentId AND subjectId = :subjectId " +
            "ORDER BY atMillis DESC, id DESC"
    )
    fun observeForStudentSubject(studentId: Long, subjectId: Long): Flow<List<RateChangeEntity>>

    @Query("SELECT * FROM price_history WHERE groupId = :groupId ORDER BY atMillis DESC, id DESC")
    fun observeForGroup(groupId: Long): Flow<List<RateChangeEntity>>

    @Insert
    suspend fun insert(change: RateChangeEntity): Long

    @Query("DELETE FROM price_history WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun deleteForStudentSubject(studentId: Long, subjectId: Long)
}

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
     */
    @Query(
        "$CLASS_ROWS WHERE l.startMillis < :toMillis AND l.startMillis + l.durationMinutes * 60000 > :nowMillis " +
            "ORDER BY l.startMillis",
    )
    suspend fun notEndedBefore(nowMillis: Long, toMillis: Long): List<ClassRow>

    /**
     * The classes that currently mirror a calendar event — the only ones turning sync off has
     * anything to undo. The alternative is reading the whole table and no-opping on most of it.
     */
    @Query("$CLASS_ROWS WHERE a.calendarEventId IS NOT NULL")
    suspend fun withCalendarEvent(): List<ClassRow>

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

    /** Lessons still planned whose end time is at or before [nowMillis] — due for auto-completion. */
    @Query(
        "SELECT * FROM lessons WHERE status = 'SCHEDULED' " +
            "AND (startMillis + durationMinutes * 60000) <= :nowMillis"
    )
    suspend fun dueForCompletion(nowMillis: Long): List<LessonEntity>

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
 * Homework, one row per piece — see [HomeworkEntity].
 *
 * **Its two writes after the first are targeted `UPDATE`s**, not a whole row written back: homework is
 * swiped done from lists that can be an emission behind, and edited in a dialog opened from one. Writing
 * the row whole from either would put back whatever the other had changed in the meantime — an edit
 * un-doing a swipe, or a swipe restoring the old text.
 */
@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework")
    fun observeAll(): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework")
    suspend fun getAll(): List<HomeworkEntity>

    /** A student's own homework and their groups' — what their card lists. */
    @Query(
        "SELECT * FROM homework WHERE studentId = :studentId " +
            "OR groupId IN (SELECT groupId FROM group_members WHERE studentId = :studentId)",
    )
    fun observeForStudent(studentId: Long): Flow<List<HomeworkEntity>>

    /** A group's own homework — what its card lists. */
    @Query("SELECT * FROM homework WHERE groupId = :groupId")
    fun observeForGroup(groupId: Long): Flow<List<HomeworkEntity>>

    /** A new row — or, with its id, one being put back by an undo. */
    @Insert
    suspend fun insert(row: HomeworkEntity): Long

    @Query("UPDATE homework SET studentId = :studentId, groupId = :groupId, text = :text WHERE id = :id")
    suspend fun edit(id: Long, studentId: Long?, groupId: Long?, text: String)

    /** Marks it done at [doneAtMillis], or current again with null. */
    @Query("UPDATE homework SET doneAtMillis = :doneAtMillis WHERE id = :id")
    suspend fun setDone(id: Long, doneAtMillis: Long?)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY dateMillis DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY dateMillis DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY dateMillis DESC")
    fun observeForStudent(studentId: Long): Flow<List<PaymentEntity>>

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    /**
     * Corrects an existing payment in place, keeping its id.
     *
     * Separate from [insert] rather than folded into it as a REPLACE: a payment is financial history,
     * and "insert, or silently overwrite whatever is already there" is the wrong default for a table
     * whose rows are money somebody handed over. An update is a deliberate correction and says so.
     */
    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)
}
