package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.TutoringClass
import kotlin.time.Instant

/**
 * One student's account, line by line: every billed lesson and every payment in time order, each with the
 * balance it left — what a parent asking "what is this for?" is sent. Its last balance is [Billing]'s, by
 * construction; `StatementTest` holds the two together.
 */
data class Statement(val entries: List<Entry>, val balance: Balance) {

    sealed interface Entry {
        val at: Instant

        /** What they owed after this line; negative is credit. */
        val balanceAfter: Money
    }

    data class Lesson(val klass: TutoringClass, val charge: Money, override val balanceAfter: Money) : Entry {
        override val at: Instant get() = klass.start
    }

    data class Paid(val payment: Payment, override val balanceAfter: Money) : Entry {
        override val at: Instant get() = payment.date
    }

    companion object {
        /**
         * [classes] and [payments] are one student's. Lessons not billed (planned, cancelled, an unbilled
         * absence) are left out; at one instant a lesson comes before the payment for it.
         */
        fun of(classes: List<TutoringClass>, payments: List<Payment>): Statement {
            val lessons = classes.mapNotNull { klass ->
                Billing.billedOrNull(listOf(klass))?.let { Lesson(klass, it, Money.ZERO) }
            }
            var owed = Money.ZERO
            val entries = (lessons + payments.map { Paid(it, Money.ZERO) })
                .sortedWith(compareBy<Entry>({ it.at }, { if (it is Lesson) 0 else 1 }))
                .map { entry ->
                    when (entry) {
                        is Lesson -> entry.copy(balanceAfter = (owed + entry.charge).also { owed = it })
                        is Paid -> entry.copy(balanceAfter = (owed - entry.payment.amount).also { owed = it })
                    }
                }
            return Statement(entries, Balance.of(owed))
        }
    }
}
