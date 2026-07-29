package com.seblful.klas.domain

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
     * `filter { it.status.isBillable }` was written out at ten sites across four modules and two view
     * models, so "what counts as income" was a convention rather than a definition — and one of the ten
     * (a group lesson's charge on its card) folded the charges without it, relying on cancelling having
     * zeroed them while the other nine did not trust that. Having one definition is also what let the
     * second half of the question — was this student in the room — be added in one place.
     *
     * A **list**, not a sum, because several callers need to group or count the billable classes before
     * adding anything up: filtering after grouping would invent a zero-earning month for a month whose
     * only lesson was cancelled.
     */
    fun billable(classes: List<TutoringClass>): List<TutoringClass> = classes.filter { it.isBillable }

    /** What [classes] earned. Idempotent over an already-[billable] list, so callers may group first. */
    fun charged(classes: List<TutoringClass>): Money =
        billable(classes).fold(Money.ZERO) { acc, c -> acc + c.charge }

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
