package com.seblful.klas.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Rows are returned unordered wherever the order would depend on **name** collation: SQLite's
// `COLLATE NOCASE` folds ASCII only, so it cannot order Russian names, and restating the real rule in
// SQL is not possible. Name ordering is applied once in Kotlin — see `format/NameOrder` — by the
// repositories. Ordering by time or id stays here, where no collation is involved.

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    fun observeAll(): Flow<List<SubjectEntity>>

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

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes WHERE id = :id")
    fun observeById(id: Long): Flow<ClassEntity?>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getById(id: Long): ClassEntity?

    @Query("SELECT * FROM classes ORDER BY startMillis")
    fun observeAll(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE studentId = :studentId ORDER BY startMillis DESC")
    fun observeForStudent(studentId: Long): Flow<List<ClassEntity>>

    /** Every class ever booked as part of [groupId] — the group card's lesson list. */
    @Query("SELECT * FROM classes WHERE groupId = :groupId ORDER BY startMillis DESC")
    fun observeForGroup(groupId: Long): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<ClassEntity>>

    /**
     * [observeBetween] as a one-shot read, for the two callers that want a window of the schedule
     * **once** rather than for as long as a screen is open: the conflict check a save makes, and the
     * reminder plan.
     *
     * Both used to get there the long way round. The conflict check collected [observeBetween] and
     * took its first value, which registers an invalidation observer and opens a channel per save,
     * move and un-cancel, only to tear all of it down again; the reminder plan read *every class ever
     * taught* to decide about the next week. A `suspend` query over the window is what both actually
     * asked for.
     */
    @Query("SELECT * FROM classes WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    suspend fun getBetween(fromMillis: Long, toMillis: Long): List<ClassEntity>

    /**
     * The classes that currently mirror a calendar event — the only ones turning sync off has
     * anything to undo. The alternative is reading the whole table and no-opping on most of it.
     */
    @Query("SELECT * FROM classes WHERE calendarEventId IS NOT NULL")
    suspend fun withCalendarEvent(): List<ClassEntity>

    /** How many classes reference [subjectId] — used to block deleting a subject with history. */
    @Query("SELECT COUNT(*) FROM classes WHERE subjectId = :subjectId")
    suspend fun countForSubject(subjectId: Long): Int

    /**
     * How many classes a student has for one subject — used to refuse un-enrolling a subject whose
     * classes (completed ones especially) are billed history that must keep referencing a price.
     */
    @Query("SELECT COUNT(*) FROM classes WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun countForStudentSubject(studentId: Long, subjectId: Long): Int

    /** How many classes reference [groupId] — used to block deleting a group with history. */
    @Query("SELECT COUNT(*) FROM classes WHERE groupId = :groupId")
    suspend fun countForGroup(groupId: Long): Int

    /**
     * The classes making up one group lesson: same group, same start. That pair is the session key —
     * a member's class moved on its own leaves the session by definition (its start no longer matches).
     */
    @Query("SELECT * FROM classes WHERE groupId = :groupId AND startMillis = :startMillis ORDER BY id")
    suspend fun getSession(groupId: Long, startMillis: Long): List<ClassEntity>

    /** Scheduled classes whose end time is at or before [nowMillis] — due for auto-completion. */
    @Query("SELECT * FROM classes WHERE status = 'SCHEDULED' AND (startMillis + durationMinutes * 60000) <= :nowMillis")
    suspend fun dueForCompletion(nowMillis: Long): List<ClassEntity>

    /**
     * One student's classes that are still merely *planned* — whatever their time. What archiving them
     * has to settle: a scheduled class is one the tutor never confirmed happened, and left behind it
     * would auto-complete into income that no report shows (see `StudentRepository.setArchived`).
     */
    @Query("SELECT * FROM classes WHERE studentId = :studentId AND status = 'SCHEDULED'")
    suspend fun scheduledForStudent(studentId: Long): List<ClassEntity>

    /** The next scheduled class starting after [nowMillis], for the reminder. */
    @Query("SELECT * FROM classes WHERE status = 'SCHEDULED' AND startMillis > :nowMillis ORDER BY startMillis LIMIT 1")
    suspend fun nextScheduledAfter(nowMillis: Long): ClassEntity?

    @Query("SELECT * FROM classes")
    suspend fun getAll(): List<ClassEntity>

    @Upsert
    suspend fun upsert(klass: ClassEntity): Long

    @Update
    suspend fun update(klass: ClassEntity)

    @Delete
    suspend fun delete(klass: ClassEntity)
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
