package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.StudentBalance
import com.seblful.tutoringapp.domain.model.TutoringClass

/**
 * Pure money arithmetic. Balance = Σ(charges of completed classes) − Σ(payments).
 * A positive balance is debt; a negative balance is credit (over-payment).
 */
object Billing {

    fun charged(classes: List<TutoringClass>): Money =
        classes.filter { it.status.isBillable }
            .fold(Money.ZERO) { acc, c -> acc + c.charge }

    fun paid(payments: List<Payment>): Money =
        payments.fold(Money.ZERO) { acc, p -> acc + p.amount }

    fun balanceOf(studentId: Long, classes: List<TutoringClass>, payments: List<Payment>): StudentBalance =
        StudentBalance(
            studentId = studentId,
            charged = charged(classes),
            paid = paid(payments),
        )
}
