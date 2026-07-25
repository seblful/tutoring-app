package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.PriceDao
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.domain.model.Money
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
     * studentId → (subjectId → that student's rate). The keys of the inner map *are* the student's
     * subjects (a subject is linked exactly when it has a price — see `EnrollmentRepository`), so
     * this one flow answers both "who takes this subject" and "at what rate", which is what the
     * group form needs while a roster is being picked for a subject.
     */
    fun observeRatesByStudent(): Flow<Map<Long, Map<Long, Money>>> =
        observeAll().map { all ->
            all.groupBy { it.studentId }
                .mapValues { (_, list) -> list.associate { it.subjectId to it.amount } }
        }

    /**
     * studentId → the subjects that student has a price for. What a group can be booked for is the
     * intersection of these across its members (see `GroupRules.bookableSubjects`), so the group
     * screens need every member's set at once rather than one student's at a time.
     */
    fun observePricedSubjectsByStudent(): Flow<Map<Long, Set<Long>>> =
        observeRatesByStudent().map { rates -> rates.mapValues { (_, bySubject) -> bySubject.keys } }

    suspend fun get(studentId: Long, subjectId: Long): Price? = dao.get(studentId, subjectId)?.toDomain()

    // Prices are written only via EnrollmentRepository, which sets a price exactly when it links the
    // subject (and deletes it when it unlinks), so the priced set always equals the subject set.
    // This repository stays read-only on purpose — no setPrice/deletePrice that could break that.
}
