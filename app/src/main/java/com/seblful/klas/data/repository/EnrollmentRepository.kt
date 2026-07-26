package com.seblful.klas.data.repository

import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.RateOwner
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
 * Every mutation below is **one transaction**, because each of them spans three tables —
 * `student_subjects`, `prices` and `price_history` — and the invariant this class exists to hold is
 * precisely that they agree. A failure between the link and the price is not a lost edit but a
 * *broken* one: a subject the student is listed as taking, at no price, which the booking form then
 * offers and which no completed class could ever be billed at. Being single-user does not help here —
 * the writes are sequential either way, and what a transaction rules out is stopping halfway.
 */
@Singleton
class EnrollmentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val priceDao: PriceDao,
    private val classDao: ClassDao,
    private val rateHistory: RateHistoryRepository,
    private val transactor: Transactor,
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

    /**
     * Adds [subjectId] to [studentId] at [price] — links it and prices it together, in **one**
     * transaction, which is what "together" has to mean to be worth saying.
     */
    suspend fun enroll(studentId: Long, subjectId: Long, price: Money) {
        transactor.transaction {
            studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
            record(studentId, subjectId, price)
        }
    }

    /**
     * Removes [subjectId] from [studentId] — unlinking it, deleting its price and forgetting that
     * price's history together. Refused ([Unenroll.HasClasses], changing nothing) when the student has
     * any class for the subject: those classes are billed history that must keep referencing a priced
     * subject — which is also why dropping the log here is safe (nothing it describes ever billed).
     */
    suspend fun unenroll(studentId: Long, subjectId: Long): Unenroll {
        // The count is read outside the transaction on purpose: it is the *decision* whether to write
        // at all, and a refusal writes nothing there is anything to roll back.
        if (classDao.countForStudentSubject(studentId, subjectId) > 0) return Unenroll.HasClasses
        transactor.transaction {
            studentDao.removeSubjectLink(studentId, subjectId)
            priceDao.delete(studentId, subjectId)
            rateHistory.forget(RateOwner.StudentSubject(studentId, subjectId))
        }
        return Unenroll.Ok
    }

    /**
     * Updates the price of a subject the student is already enrolled in, recording the change. One
     * transaction: the new price and the log entry saying what it was are the same fact written twice,
     * so a rate that changed without being logged is exactly the history the log exists to prevent.
     */
    suspend fun reprice(studentId: Long, subjectId: Long, price: Money) {
        transactor.transaction { record(studentId, subjectId, price) }
    }

    /**
     * Replaces a student's enrollments wholesale with [pricedSubjects] (subjectId → price). Used at
     * creation (the add-student form and onboarding): links the chosen subjects, sets each price,
     * and drops any subject — link and price together — no longer present, keeping the two in step.
     *
     * The widest write here, and so the one that most needs to be **one** transaction: a save that
     * added three subjects and stopped after the second would leave the student taking a subject at no
     * price, and the tutor with no way to tell which of the three it was.
     */
    suspend fun setEnrollments(studentId: Long, pricedSubjects: Map<Long, Money>) {
        val current = studentDao.getSubjectIds(studentId).toSet()
        val target = pricedSubjects.keys
        transactor.transaction {
            (target - current).forEach { studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, it)) }
            (current - target).forEach {
                studentDao.removeSubjectLink(studentId, it)
                priceDao.delete(studentId, it)
                rateHistory.forget(RateOwner.StudentSubject(studentId, it))
            }
            pricedSubjects.forEach { (subjectId, price) -> record(studentId, subjectId, price) }
        }
    }

    /**
     * Writes the price **and** logs it — the one path by which a `prices` row changes, so the history
     * cannot be skipped. Reads the previous amount first and hands both to
     * [RateHistoryRepository.record], which drops the write when the rate did not actually move: this
     * method is called with an unchanged amount on every student-form save, and a log full of
     * "25 → 25" would bury the changes the tutor came to read.
     *
     * **The caller owns the transaction.** Every public mutation above opens one, so this deliberately
     * does not: opening a second inside them would nest for no gain, and the two writes it makes are
     * only ever part of a larger edit that has to land whole anyway.
     */
    private suspend fun record(studentId: Long, subjectId: Long, price: Money) {
        val previous = priceDao.get(studentId, subjectId)?.let { Money(it.amountMinor) }
        priceDao.upsert(Price(studentId, subjectId, price).toEntity())
        rateHistory.record(RateOwner.StudentSubject(studentId, subjectId), from = previous, to = price)
    }
}
