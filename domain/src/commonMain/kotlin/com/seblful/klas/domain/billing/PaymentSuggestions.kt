package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money

/**
 * The amounts a new payment offers at one tap: what settles the balance — the commonest payment, which then
 * takes no typing — and what the last lesson cost. Each is null when it would say nothing; the lesson's is
 * dropped when it is the balance, so two chips never offer one amount.
 */
data class PaymentSuggestions(val fullBalance: Money?, val oneLesson: Money?) {

    companion object {
        val NONE = PaymentSuggestions(fullBalance = null, oneLesson = null)

        fun of(balance: Balance, lastLessonCharge: Money?): PaymentSuggestions {
            val owed = (balance as? Balance.Owed)?.amount
            val lesson = lastLessonCharge?.takeIf { it.minor > 0 && it != owed }
            return PaymentSuggestions(owed, lesson)
        }
    }
}
