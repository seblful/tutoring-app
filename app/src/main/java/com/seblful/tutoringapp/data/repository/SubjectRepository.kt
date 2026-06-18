package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.SubjectDao
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.model.Subject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao,
) {
    fun observeAll(): Flow<List<Subject>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Subject? = dao.getById(id)?.toDomain()

    suspend fun upsert(subject: Subject): Long = dao.upsert(subject.toEntity())

    suspend fun delete(subject: Subject) = dao.delete(subject.toEntity())
}
