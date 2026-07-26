package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.PriceDao
import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Price
import com.seblful.repetit.domain.model.RateOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the invariant that **a student's subjects are exactly their priced subjects** — the single
 * source of truth for what can be booked. A subject link and its price are always created and
 * removed together here, so the two can never diverge, and a subject with billed history can't be
 * un-enrolled. Every caller that changes a student's subjects goes through this one seam, instead of
 * pairing a link operation with a price operation by hand (which is how the invariant used to be
 * "enforced" — by caller discipline spread across the student view models).
 *
 * Setting a price is also what **logs** it: every write here goes through [record], so the rate
 * history can't be missed by a caller that changed a price and forgot to say so (see
 * [RateHistoryRepository]).
 *
 * Mutations are sequenced, not wrapped in a DB transaction: the app is single-user and offline, and
 * this matches the prior behaviour. The win is one home for the rule, not cross-table atomicity.
 */
@Singleton
class EnrollmentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val priceDao: PriceDao,
    private val classDao: ClassDao,
    private val rateHistory: RateHistoryRepository,
) {
    // ---- Reads ---------------------------------------------------------------------------------
    // Prices are read here rather than from a repository of their own. There was one, and every
    // method on it was `dao.x().map { it.toDomain() }` — so the fact a student's subjects *are*
    // their priced subjects was written here while the way to look it up lived somewhere else.

    /** The student's priced subjects — which is to say, the subjects they take. */
    fun observePricesFor(studentId: Long): Flow<List<Price>> =
        priceDao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /** The student's price for one subject, or null when they don't take it. */
    suspend fun priceFor(studentId: Long, subjectId: Long): Price? =
        priceDao.get(studentId, subjectId)?.toDomain()

    /** The outcome of [unenroll]. */
    sealed interface Unenroll {
        /** The subject was unlinked and its price removed. */
        data object Ok : Unenroll

        /** Refused: the student has classes for the subject, so it must keep its price. */
        data object HasClasses : Unenroll
    }

    /** Adds [subjectId] to [studentId] at [price] — links it and prices it together. */
    suspend fun enroll(studentId: Long, subjectId: Long, price: Money) {
        studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
        record(studentId, subjectId, price)
    }

    /**
     * Removes [subjectId] from [studentId] — unlinking it, deleting its price and forgetting that
     * price's history together. Refused ([Unenroll.HasClasses], changing nothing) when the student has
     * any class for the subject: those classes are billed history that must keep referencing a priced
     * subject — which is also why dropping the log here is safe (nothing it describes ever billed).
     */
    suspend fun unenroll(studentId: Long, subjectId: Long): Unenroll {
        if (classDao.countForStudentSubject(studentId, subjectId) > 0) return Unenroll.HasClasses
        studentDao.removeSubjectLink(studentId, subjectId)
        priceDao.delete(studentId, subjectId)
        rateHistory.forget(RateOwner.StudentSubject(studentId, subjectId))
        return Unenroll.Ok
    }

    /** Updates the price of a subject the student is already enrolled in, recording the change. */
    suspend fun reprice(studentId: Long, subjectId: Long, price: Money) {
        record(studentId, subjectId, price)
    }

    /**
     * Replaces a student's enrollments wholesale with [pricedSubjects] (subjectId → price). Used at
     * creation (the add-student form and onboarding): links the chosen subjects, sets each price,
     * and drops any subject — link and price together — no longer present, keeping the two in step.
     */
    suspend fun setEnrollments(studentId: Long, pricedSubjects: Map<Long, Money>) {
        val current = studentDao.getSubjectIds(studentId).toSet()
        val target = pricedSubjects.keys
        (target - current).forEach { studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, it)) }
        (current - target).forEach {
            studentDao.removeSubjectLink(studentId, it)
            priceDao.delete(studentId, it)
            rateHistory.forget(RateOwner.StudentSubject(studentId, it))
        }
        pricedSubjects.forEach { (subjectId, price) -> record(studentId, subjectId, price) }
    }

    /**
     * Writes the price **and** logs it — the one path by which a `prices` row changes, so the history
     * cannot be skipped. Reads the previous amount first and hands both to
     * [RateHistoryRepository.record], which drops the write when the rate did not actually move: this
     * method is called with an unchanged amount on every student-form save, and a log full of
     * "25 → 25" would bury the changes the tutor came to read.
     */
    private suspend fun record(studentId: Long, subjectId: Long, price: Money) {
        val previous = priceDao.get(studentId, subjectId)?.let { Money(it.amountMinor) }
        priceDao.upsert(Price(studentId, subjectId, price).toEntity())
        rateHistory.record(RateOwner.StudentSubject(studentId, subjectId), from = previous, to = price)
    }
}
