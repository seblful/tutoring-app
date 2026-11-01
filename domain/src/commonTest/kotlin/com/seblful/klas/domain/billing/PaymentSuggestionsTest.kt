package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentSuggestionsTest {

    @Test
    fun `a student who owes is offered the whole balance and one lesson`() {
        assertEquals(
            PaymentSuggestions(fullBalance = Money(9000), oneLesson = Money(3000)),
            PaymentSuggestions.of(Balance.Owed(Money(9000)), lastLessonCharge = Money(3000)),
        )
    }

    @Test
    fun `nothing owed offers no balance but a lesson in advance`() {
        assertEquals(
            PaymentSuggestions(fullBalance = null, oneLesson = Money(3000)),
            PaymentSuggestions.of(Balance.Settled, lastLessonCharge = Money(3000)),
        )
        assertEquals(
            PaymentSuggestions(fullBalance = null, oneLesson = Money(3000)),
            PaymentSuggestions.of(Balance.Credit(Money(500)), lastLessonCharge = Money(3000)),
        )
    }

    @Test
    fun `one amount is never offered twice and a free lesson is not offered`() {
        assertEquals(
            PaymentSuggestions(fullBalance = Money(3000), oneLesson = null),
            PaymentSuggestions.of(Balance.Owed(Money(3000)), lastLessonCharge = Money(3000)),
        )
        assertEquals(PaymentSuggestions.NONE, PaymentSuggestions.of(Balance.Settled, lastLessonCharge = Money.ZERO))
        assertEquals(PaymentSuggestions.NONE, PaymentSuggestions.of(Balance.Settled, lastLessonCharge = null))
    }
}
