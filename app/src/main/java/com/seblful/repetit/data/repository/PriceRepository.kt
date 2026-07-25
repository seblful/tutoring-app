package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.PriceDao
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.domain.model.Price
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val dao: PriceDao,
) {
    fun observeForStudent(studentId: Long): Flow<List<Price>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Price>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * studentId → the subjects that student has a price for. What a group can be booked for is the
     * intersection of these across its members (see `GroupRules.bookableSubjects`), so the group
     * screens need every member's set at once rather than one student's at a time.
     */
    fun observePricedSubjectsByStudent(): Flow<Map<Long, Set<Long>>> =
        dao.observeAll().map { all ->
            all.groupBy { it.studentId }.mapValues { (_, list) -> list.map { it.subjectId }.toSet() }
        }

    suspend fun get(studentId: Long, subjectId: Long): Price? = dao.get(studentId, subjectId)?.toDomain()

    // Prices are written only via EnrollmentRepository, which sets a price exactly when it links the
    // subject (and deletes it when it unlinks), so the priced set always equals the subject set.
    // This repository stays read-only on purpose — no setPrice/deletePrice that could break that.
}
