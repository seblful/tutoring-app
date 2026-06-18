package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.PaymentDao
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.model.Payment
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
