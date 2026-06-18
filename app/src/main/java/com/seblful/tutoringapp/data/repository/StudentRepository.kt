package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.StudentDao
import com.seblful.tutoringapp.data.local.StudentSubjectCrossRef
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.model.Student
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

    /** Replaces the student's set of subjects with [subjectIds]. */
    suspend fun setSubjects(studentId: Long, subjectIds: Set<Long>) {
        val current = dao.getSubjectIds(studentId).toSet()
        (subjectIds - current).forEach { dao.addSubjectLink(StudentSubjectCrossRef(studentId, it)) }
        (current - subjectIds).forEach { dao.removeSubjectLink(studentId, it) }
    }
}
