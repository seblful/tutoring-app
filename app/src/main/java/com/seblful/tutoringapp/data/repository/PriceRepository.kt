package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.PriceDao
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Price
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

    suspend fun get(studentId: Long, subjectId: Long): Price? = dao.get(studentId, subjectId)?.toDomain()

    suspend fun setPrice(studentId: Long, subjectId: Long, amount: Money) =
        dao.upsert(Price(studentId, subjectId, amount).toEntity())

    /**
     * Removes the price for one (student, subject). Only safe when the student has no class for that
     * subject — a completed class snapshots its own charge but still references the subject, and a
     * priced subject is the single source of truth for what's bookable. The caller
     * ([StudentDetailViewModel.removeSubject]) enforces the "no classes" precondition.
     */
    suspend fun deletePrice(studentId: Long, subjectId: Long) = dao.delete(studentId, subjectId)
}
