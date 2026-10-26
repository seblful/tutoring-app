package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.TutoringClass

/**
 * Pure money arithmetic. Balance = Σ(charges of completed classes) − Σ(payments).
 * A positive balance is debt; a negative balance is credit (over-payment).
 */
object Billing {

    /**
     * Of [classes], the ones that count as income — the lesson happened **and** this student owes for it
     * ([TutoringClass.isBillable]).
     *
     * The single definition of "what counts as income": callers do not filter on status themselves, so
     * nobody folds charges without it by relying on cancelling having zeroed them, and the second half of
     * the question — was this student in the room — lives in one place.
     *
     * A **list**, not a sum, because several callers need to group or count the billable classes before
     * adding anything up: filtering after grouping would invent a zero-earning month for a month whose
     * only lesson was cancelled.
     */
    fun billable(classes: List<TutoringClass>): List<TutoringClass> = classes.filter { it.isBillable }

    /** What [classes] earned. Idempotent over an already-[billable] list, so callers may group first. */
    fun charged(classes: List<TutoringClass>): Money =
        billable(classes).fold(Money.ZERO) { acc, c -> acc + c.charge }

    /**
     * What a lesson row says it came to: what [classes] earned, or null when they billed nobody.
     *
     * Null rather than zero, because "billed nothing" and "billed zero" are different rows: a lesson still
     * to come, a cancelled one, or one this student missed unbilled earned nothing and shows no amount,
     * while a taught lesson at a zero rate did earn — zero — and says so. Neither `status == COMPLETED`
     * nor `!charge.isZero` tells these apart.
     */
    fun billedOrNull(classes: List<TutoringClass>): Money? =
        billable(classes).takeIf { it.isNotEmpty() }?.let(::charged)

    fun paid(payments: List<Payment>): Money =
        payments.fold(Money.ZERO) { acc, p -> acc + p.amount }

    fun balanceOf(studentId: Long, classes: List<TutoringClass>, payments: List<Payment>): StudentBalance =
        StudentBalance(
            studentId = studentId,
            charged = charged(classes),
            paid = paid(payments),
        )

    /**
     * One [StudentBalance] per student in [students] (aligned with that order), built from the
     * full class and payment lists. A student with no classes or payments gets a zero balance.
     */
    fun balancesFor(
        students: List<Student>,
        classes: List<TutoringClass>,
        payments: List<Payment>,
    ): List<StudentBalance> {
        val classesByStudent = classes.groupBy { it.studentId }
        val paymentsByStudent = payments.groupBy { it.studentId }
        return students.map { student ->
            balanceOf(
                student.id,
                classesByStudent[student.id].orEmpty(),
                paymentsByStudent[student.id].orEmpty(),
            )
        }
    }
}
