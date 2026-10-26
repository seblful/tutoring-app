package com.seblful.klas.data.people

import com.seblful.klas.data.billing.PriceDao
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the invariant that **a student's subjects are exactly their priced subjects** — the single
 * source of truth for what can be booked. A subject link and its price are always created and
 * removed together here, so the two can never diverge, and a subject with billed history can't be
 * un-enrolled.
 */
@SingleIn(AppScope::class)
class EnrollmentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val priceDao: PriceDao,

    private val classes: ClassRepository,
    private val rateHistory: RateHistoryRepository,
    private val transactor: Transactor,
    private val students: StudentRepository,
) {
    // ---- Reads ---------------------------------------------------------------------------------
    // Prices are read here rather than from a repository of their own. There was one, and every
    // method on it was `dao.x().map { it.toDomain() }` — so the fact a student's subjects *are*
    // their priced subjects was written here while the way to look it up lived somewhere else.

    /** The student's priced subjects — which is to say, the subjects they take. */
    fun observePricesFor(studentId: Long): Flow<List<Price>> =
        priceDao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /** Every student's rate for every subject they take: what the subjects page says each is used at. */
    fun observeAllPrices(): Flow<List<Price>> = priceDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Every student's subject ids, keyed by student; one who takes nothing yet has no entry. */
    fun observeSubjectIds(): Flow<Map<Long, List<Long>>> =
        priceDao.observeEnrollments().map { rows ->
            rows.groupBy({ it.studentId }, { it.subjectId })
        }

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
     * Creates [student] **with** the subjects they are priced for, in one transaction, and returns the
     * new id.
     */
    suspend fun createStudent(student: Student, pricedSubjects: Map<Long, Money>): Long =
        transactor.transaction {
            // This is a creation, so the upsert inserts — but the -1 sentinel is handled all the
            // same (see [upsertedId]) rather than trusted not to occur.
            val id = upsertedId(studentDao.upsert(student.toEntity()), student.id)
            pricedSubjects.forEach { (subjectId, price) -> link(id, subjectId, price) }
            id
        }

    /**
     * Adds [subjectId] to [studentId] at [price] — links it and prices it together, in **one**
     * transaction, which is what "together" has to mean to be worth saying.
     */
    suspend fun enroll(studentId: Long, subjectId: Long, price: Money) {
        transactor.transaction { link(studentId, subjectId, price) }
    }

    /**
     * Removes [subjectId] from [studentId] — unlinking it, deleting its price and forgetting that
     * price's history together. Refused ([Unenroll.HasClasses], changing nothing) when the student has
     * any class for the subject: those classes are billed history that must keep referencing a priced
     * subject — which is also why dropping the log here is safe (nothing it describes ever billed).
     */
    suspend fun unenroll(studentId: Long, subjectId: Long): Unenroll {
        // A cheap refusal avoids opening a transaction; the write must recheck after acquiring its lock.
        if (classes.anyForStudentSubject(studentId, subjectId)) return Unenroll.HasClasses
        return transactor.transaction {
            if (classes.anyForStudentSubject(studentId, subjectId)) Unenroll.HasClasses
            else {
                unlink(studentId, subjectId)
                Unenroll.Ok
            }
        }
    }

    /** The subjects [studentId] takes and has lessons in: billed history that keeps each on them ([unenroll]). */
    suspend fun subjectsWithLessons(studentId: Long): Set<Long> =
        studentDao.getSubjectIds(studentId).filterTo(mutableSetOf()) { classes.anyForStudentSubject(studentId, it) }

    /**
     * Makes [pricedSubjects] what [studentId] takes, as the student form saves it, in one transaction: a new
     * subject is enrolled, a rate that moved is re-priced (and logged), and a subject left out is unenrolled —
     * unless it has lessons, which keep it, as [unenroll] would.
     */
    suspend fun setSubjects(studentId: Long, pricedSubjects: Map<Long, Money>) {
        transactor.transaction { applySubjects(studentId, pricedSubjects) }
    }

    /**
     * Saves [student] and makes [pricedSubjects] what they take, as the student form's edit does, in one
     * transaction: a name or an address never lands without the rates saved with it.
     */
    suspend fun updateStudent(student: Student, pricedSubjects: Map<Long, Money>) {
        val written = transactor.transaction {
            students.writeRow(student).also { applySubjects(student.id, pricedSubjects) }
        }
        written.refreshMirrors()
    }

    private suspend fun applySubjects(studentId: Long, pricedSubjects: Map<Long, Money>) {
        val kept = subjectsWithLessons(studentId)
        (studentDao.getSubjectIds(studentId) - pricedSubjects.keys - kept).forEach { unlink(studentId, it) }
        pricedSubjects.forEach { (subjectId, price) ->
            when (priceDao.get(studentId, subjectId)?.toDomain()?.amount) {
                null -> link(studentId, subjectId, price)
                price -> Unit
                else -> record(studentId, subjectId, price)
            }
        }
    }

    /**
     * Updates the price of a subject the student is already enrolled in, recording the change. One
     * transaction: the new price and the log entry saying what it was are the same fact written twice,
     * so a rate that changed without being logged is exactly the history the log exists to prevent.
     */
    suspend fun reprice(studentId: Long, subjectId: Long, price: Money) {
        transactor.transaction { record(studentId, subjectId, price) }
    }

    private suspend fun link(studentId: Long, subjectId: Long, price: Money) {
        studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
        record(studentId, subjectId, price)
    }

    private suspend fun unlink(studentId: Long, subjectId: Long) {
        studentDao.removeSubjectLink(studentId, subjectId)
        priceDao.delete(studentId, subjectId)
        rateHistory.forget(RateOwner.StudentSubject(studentId, subjectId))
    }

    private suspend fun record(studentId: Long, subjectId: Long, price: Money) {
        val previous = rateHistory.currentRate(RateOwner.StudentSubject(studentId, subjectId))
        priceDao.upsert(Price(studentId, subjectId, price).toEntity())
        rateHistory.record(RateOwner.StudentSubject(studentId, subjectId), from = previous, to = price)
    }
}
