package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.PriceDao
import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Price
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
 * Mutations are sequenced, not wrapped in a DB transaction: the app is single-user and offline, and
 * this matches the prior behaviour. The win is one home for the rule, not cross-table atomicity.
 */
@Singleton
class EnrollmentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val priceDao: PriceDao,
    private val classDao: ClassDao,
) {
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
        priceDao.upsert(Price(studentId, subjectId, price).toEntity())
    }

    /**
     * Removes [subjectId] from [studentId] — unlinking it and deleting its price together. Refused
     * ([Unenroll.HasClasses], changing nothing) when the student has any class for the subject:
     * those classes are billed history that must keep referencing a priced subject.
     */
    suspend fun unenroll(studentId: Long, subjectId: Long): Unenroll {
        if (classDao.countForStudentSubject(studentId, subjectId) > 0) return Unenroll.HasClasses
        studentDao.removeSubjectLink(studentId, subjectId)
        priceDao.delete(studentId, subjectId)
        return Unenroll.Ok
    }

    /** Updates the price of a subject the student is already enrolled in. */
    suspend fun reprice(studentId: Long, subjectId: Long, price: Money) {
        priceDao.upsert(Price(studentId, subjectId, price).toEntity())
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
        }
        pricedSubjects.forEach { (subjectId, price) ->
            priceDao.upsert(Price(studentId, subjectId, price).toEntity())
        }
    }
}
