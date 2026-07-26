package com.seblful.klas.data.repository

import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val dao: PaymentDao,
) {
    fun observeAll(): Flow<List<Payment>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<Payment>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    suspend fun add(payment: Payment): Long = dao.insert(payment.toEntity())

    suspend fun delete(payment: Payment) = dao.delete(payment.toEntity())
}
