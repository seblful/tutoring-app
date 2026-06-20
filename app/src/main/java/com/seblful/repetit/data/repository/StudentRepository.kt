package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dao: StudentDao,
) {
    fun observeActive(): Flow<List<Student>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Student>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Student?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Student? = dao.getById(id)?.toDomain()

    suspend fun upsert(student: Student): Long = dao.upsert(student.toEntity())

    suspend fun setArchived(student: Student, archived: Boolean) {
        dao.upsert(student.copy(archived = archived).toEntity())
    }

    fun observeSubjectIds(studentId: Long): Flow<List<Long>> = dao.observeSubjectIds(studentId)

    /** Replaces the student's set of subjects with [subjectIds]. Used at creation (form/onboarding). */
    suspend fun setSubjects(studentId: Long, subjectIds: Set<Long>) {
        val current = dao.getSubjectIds(studentId).toSet()
        (subjectIds - current).forEach { dao.addSubjectLink(StudentSubjectCrossRef(studentId, it)) }
        (current - subjectIds).forEach { dao.removeSubjectLink(studentId, it) }
    }

    /**
     * Links one subject to a student. The student's subject set is kept in lock-step with their
     * prices (see [StudentDetailViewModel]): linking is always paired with setting a price, so the
     * displayed subject list and the bookable (priced) set can never diverge.
     */
    suspend fun linkSubject(studentId: Long, subjectId: Long) =
        dao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))

    /** Unlinks one subject from a student (paired with deleting its price). */
    suspend fun unlinkSubject(studentId: Long, subjectId: Long) =
        dao.removeSubjectLink(studentId, subjectId)
}
