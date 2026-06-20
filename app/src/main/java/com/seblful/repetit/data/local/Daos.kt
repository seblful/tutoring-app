package com.seblful.repetit.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Upsert
    suspend fun upsert(subject: SubjectEntity): Long

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Insert
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: Long): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): StudentEntity?

    @Upsert
    suspend fun upsert(student: StudentEntity): Long

    @Insert
    suspend fun insertAll(students: List<StudentEntity>)

    @Query("SELECT subjectId FROM student_subjects WHERE studentId = :studentId")
    fun observeSubjectIds(studentId: Long): Flow<List<Long>>

    @Query("SELECT subjectId FROM student_subjects WHERE studentId = :studentId")
    suspend fun getSubjectIds(studentId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSubjectLink(ref: StudentSubjectCrossRef)

    @Query("DELETE FROM student_subjects WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun removeSubjectLink(studentId: Long, subjectId: Long)

    @Query("SELECT * FROM student_subjects")
    suspend fun getAllLinks(): List<StudentSubjectCrossRef>

    @Insert
    suspend fun insertLinks(links: List<StudentSubjectCrossRef>)

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Query("DELETE FROM student_subjects")
    suspend fun deleteAllLinks()
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

    @Query("SELECT * FROM prices")
    suspend fun getAll(): List<PriceEntity>

    @Insert
    suspend fun insertAll(prices: List<PriceEntity>)

    @Query("DELETE FROM prices")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM classes WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE studentId = :studentId")
    suspend fun getForStudent(studentId: Long): List<ClassEntity>

    /** How many classes reference [subjectId] — used to block deleting a subject with history. */
    @Query("SELECT COUNT(*) FROM classes WHERE subjectId = :subjectId")
    suspend fun countForSubject(subjectId: Long): Int

    /**
     * How many classes a student has for one subject — used to refuse un-enrolling a subject whose
     * classes (completed ones especially) are billed history that must keep referencing a price.
     */
    @Query("SELECT COUNT(*) FROM classes WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun countForStudentSubject(studentId: Long, subjectId: Long): Int

    /** Scheduled classes whose end time is at or before [nowMillis] — due for auto-completion. */
    @Query("SELECT * FROM classes WHERE status = 'SCHEDULED' AND (startMillis + durationMinutes * 60000) <= :nowMillis")
    suspend fun dueForCompletion(nowMillis: Long): List<ClassEntity>

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

    @Insert
    suspend fun insertAll(classes: List<ClassEntity>)

    @Query("DELETE FROM classes")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM payments WHERE studentId = :studentId")
    suspend fun getForStudent(studentId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Insert
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
