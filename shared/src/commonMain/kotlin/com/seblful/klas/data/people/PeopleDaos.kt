package com.seblful.klas.data.people

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
