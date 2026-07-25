package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dao: StudentDao,
) {
    fun observeActive(): Flow<List<Student>> =
        dao.observeActive().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    /** Every student, archived ones last — they stay listed, out of the way of the active roster. */
    fun observeAll(): Flow<List<Student>> = dao.observeAll().map { list ->
        NameOrder.sortedArchivedLast(list.map { it.toDomain() }, archived = { it.archived }, name = { it.name })
    }

    fun observeById(id: Long): Flow<Student?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Student? = dao.getById(id)?.toDomain()

    suspend fun upsert(student: Student): Long = dao.upsert(student.toEntity())

    suspend fun setArchived(student: Student, archived: Boolean) {
        dao.upsert(student.copy(archived = archived).toEntity())
    }

    fun observeSubjectIds(studentId: Long): Flow<List<Long>> = dao.observeSubjectIds(studentId)

    /** Who is priced for [subjectId] — the candidates for a group bound to that subject. */
    fun observeIdsForSubject(subjectId: Long): Flow<List<Long>> = dao.observeStudentIdsForSubject(subjectId)

    suspend fun getIdsForSubject(subjectId: Long): List<Long> = dao.getStudentIdsForSubject(subjectId)

    // A student's subjects and their prices are kept in lock-step by EnrollmentRepository — the one
    // seam that links/unlinks a subject and sets/deletes its price together. Don't add link-only or
    // price-only mutators here; they let the displayed subjects and the bookable (priced) set diverge.
}
