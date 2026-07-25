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

    suspend fun get(studentId: Long, subjectId: Long): Price? = dao.get(studentId, subjectId)?.toDomain()

    // There were two cross-student reads here — every student's rates, and every student's priced
    // subject set — and both existed only to work out what a *group* could be booked for, back when a
    // group lesson billed each member their own price. A group now carries its own rate, so nothing
    // needs prices across students any more and they are gone rather than left to rot.

    // Prices are written only via EnrollmentRepository, which sets a price exactly when it links the
    // subject (and deletes it when it unlinks), so the priced set always equals the subject set.
    // This repository stays read-only on purpose — no setPrice/deletePrice that could break that.
}
