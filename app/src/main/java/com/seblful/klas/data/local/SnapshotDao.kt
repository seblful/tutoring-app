package com.seblful.klas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Every row in the database, table by table — the shape a whole-database read or write takes.
 *
 * Rows are the **persisted entities, ids included**, not domain objects: this is a copy of the
 * database, so it has to reproduce one. Nothing here is interpreted, which is why a restore can carry
 * data a later build reads differently (see `BackupIntegrity`).
 *
 * `settings` is absent, and that is the one deliberate hole: it is device-local — the calendar it
 * mirrors to, the reminder lead time, whether onboarding is done — and restoring another device's
 * settings onto this one would point the calendar mirror at a calendar that does not exist here.
 * `BackupSchemaCoverageTest` is the list that makes that a decision rather than an omission.
 */
data class DatabaseSnapshot(
    val subjects: List<SubjectEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val studentSubjects: List<StudentSubjectCrossRef> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val groupMembers: List<GroupMemberCrossRef> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    /** The rate log (`price_history`) — the record of what prices used to be. */
    val rateHistory: List<RateChangeEntity> = emptyList(),
    // A lesson and the students in it are two tables, so a snapshot carries both — and their order
    // matters twice over: `attendance` points at `lessons`, so lessons go in first and out last.
    val lessons: List<LessonEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
)

/**
 * The whole database as one thing: [read] it, or [replace] it outright.
 *
 * This is a **second interface onto the same tables**, and a deliberately separate one. The DAOs beside
 * it answer the questions a rule needs — the classes in this window, is this subject still taught, this
 * student's prices — and every repository is handed one. Backup and restore need something else
 * entirely: whole tables, ids preserved, no rule applied. Those 25 methods used to live on the same
 * eight DAOs, so every repository was also handed `deleteAll()` for its table. A module that exists to
 * hold an invariant should not be able to empty the table the invariant is about, and nothing but the
 * *type* of what it was injected with stood in the way.
 *
 * The per-table methods are `protected` for the same reason. Both orders below — children before
 * parents on the way out, parents before children on the way in — are properties of the schema's
 * foreign keys, so they are stated here, once, next to the entities that declare them, rather than in
 * whichever caller happens to be replacing the database. Getting either wrong is a foreign-key failure
 * at run time, and the only way to be sure a caller cannot get it wrong is to leave it no choice.
 *
 * [replace] is **not** `@Transaction`. A restore must be all-or-nothing, and the caller wraps it in
 * `Transactor.transaction` — the port, so the guarantee is one a JVM test can count rather than one
 * only a device can show (see `RecordingTransactor`).
 */
@Dao
abstract class SnapshotDao {

    /** Every row there is. */
    suspend fun read(): DatabaseSnapshot = DatabaseSnapshot(
        subjects = subjects(),
        students = students(),
        studentSubjects = studentSubjects(),
        groups = groups(),
        groupMembers = groupMembers(),
        prices = prices(),
        rateHistory = rateHistory(),
        lessons = lessons(),
        attendance = attendance(),
        payments = payments(),
    )

    /**
     * Throws away everything and puts [snapshot] there instead — a full replace, never a merge, because
     * the ids come with it and half of an imported database is not a database.
     *
     * Wrap this in a transaction. On its own it will happily leave the tables empty if it fails partway
     * through: the wipe is the first thing it does.
     */
    suspend fun replace(snapshot: DatabaseSnapshot) {
        clear()
        insert(snapshot)
    }

    /**
     * Adds [snapshot]'s rows to whatever is already there, **parents before children**, so every foreign
     * key has something to point at.
     *
     * [replace] is what a restore wants. This is for the instrumented tests, which need a Room database
     * put into a given state and have no repository layer to go through — and which would otherwise
     * order the tables themselves and be the second place the schema's foreign keys are written down.
     */
    suspend fun insert(snapshot: DatabaseSnapshot) {
        insertSubjects(snapshot.subjects)
        insertStudents(snapshot.students)
        insertStudentSubjects(snapshot.studentSubjects)
        insertGroups(snapshot.groups)
        insertGroupMembers(snapshot.groupMembers)
        insertPrices(snapshot.prices)
        insertRateHistory(snapshot.rateHistory)
        insertLessons(snapshot.lessons)
        insertAttendance(snapshot.attendance)
        insertPayments(snapshot.payments)
    }

    /** Empties every table, **children before parents**, so no foreign key is ever left dangling. */
    private suspend fun clear() {
        clearPayments()
        clearAttendance()
        clearLessons()
        clearRateHistory()
        clearPrices()
        clearGroupMembers()
        clearGroups()
        clearStudentSubjects()
        clearStudents()
        clearSubjects()
    }

    // ---- Reads -------------------------------------------------------------------------------------
    // `student_groups` is also read whole by a repository, and the two lesson tables are read through the
    // `classes` view, so some of these queries have a near-twin elsewhere. That is two interfaces asking
    // the same tables for different things, not one interface saying the same thing twice: a repository
    // reading every class reads it joined, maps it to the domain and applies a rule, and cannot be handed
    // the wipe that sits beside this one.

    @Query("SELECT * FROM subjects")
    protected abstract suspend fun subjects(): List<SubjectEntity>

    @Query("SELECT * FROM students")
    protected abstract suspend fun students(): List<StudentEntity>

    @Query("SELECT * FROM student_subjects")
    protected abstract suspend fun studentSubjects(): List<StudentSubjectCrossRef>

    @Query("SELECT * FROM student_groups")
    protected abstract suspend fun groups(): List<GroupEntity>

    @Query("SELECT * FROM group_members")
    protected abstract suspend fun groupMembers(): List<GroupMemberCrossRef>

    @Query("SELECT * FROM prices")
    protected abstract suspend fun prices(): List<PriceEntity>

    @Query("SELECT * FROM price_history")
    protected abstract suspend fun rateHistory(): List<RateChangeEntity>

    @Query("SELECT * FROM lessons")
    protected abstract suspend fun lessons(): List<LessonEntity>

    @Query("SELECT * FROM attendance")
    protected abstract suspend fun attendance(): List<AttendanceEntity>

    @Query("SELECT * FROM payments")
    protected abstract suspend fun payments(): List<PaymentEntity>

    // ---- Inserts -----------------------------------------------------------------------------------
    // Plain `@Insert`, not `@Upsert`: the tables are empty by the time these run, and a row that
    // collides is a corrupt file rather than something to quietly overwrite.

    @Insert
    protected abstract suspend fun insertSubjects(rows: List<SubjectEntity>)

    @Insert
    protected abstract suspend fun insertStudents(rows: List<StudentEntity>)

    @Insert
    protected abstract suspend fun insertStudentSubjects(rows: List<StudentSubjectCrossRef>)

    @Insert
    protected abstract suspend fun insertGroups(rows: List<GroupEntity>)

    @Insert
    protected abstract suspend fun insertGroupMembers(rows: List<GroupMemberCrossRef>)

    @Insert
    protected abstract suspend fun insertPrices(rows: List<PriceEntity>)

    @Insert
    protected abstract suspend fun insertRateHistory(rows: List<RateChangeEntity>)

    @Insert
    protected abstract suspend fun insertLessons(rows: List<LessonEntity>)

    @Insert
    protected abstract suspend fun insertAttendance(rows: List<AttendanceEntity>)

    @Insert
    protected abstract suspend fun insertPayments(rows: List<PaymentEntity>)

    // ---- Wipes -------------------------------------------------------------------------------------
    // The only whole-table deletes in the app. `StoredEnumNamesTest`'s sibling in
    // `data/local/WholeTableWritesTest` keeps it that way.

    @Query("DELETE FROM subjects")
    protected abstract suspend fun clearSubjects()

    @Query("DELETE FROM students")
    protected abstract suspend fun clearStudents()

    @Query("DELETE FROM student_subjects")
    protected abstract suspend fun clearStudentSubjects()

    @Query("DELETE FROM student_groups")
    protected abstract suspend fun clearGroups()

    @Query("DELETE FROM group_members")
    protected abstract suspend fun clearGroupMembers()

    @Query("DELETE FROM prices")
    protected abstract suspend fun clearPrices()

    @Query("DELETE FROM price_history")
    protected abstract suspend fun clearRateHistory()

    @Query("DELETE FROM lessons")
    protected abstract suspend fun clearLessons()

    @Query("DELETE FROM attendance")
    protected abstract suspend fun clearAttendance()

    @Query("DELETE FROM payments")
    protected abstract suspend fun clearPayments()
}
