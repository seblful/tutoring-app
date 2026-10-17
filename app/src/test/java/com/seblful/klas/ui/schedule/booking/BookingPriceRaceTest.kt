package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingPriceRaceTest {
    @Test
    fun `a stale student's price never fills the next student's lesson`() {
        val context = BookingContext()
        var booking = BookingForm()
        listOf(
            BookingIntent.ChooseStudent(1), BookingIntent.ChooseSubject(7),
            BookingIntent.ChooseStudent(2), BookingIntent.ChooseSubject(7),
            BookingIntent.PriceLoaded(studentId = 1, subjectId = 7, amount = Money.ofMajor(30)),
            BookingIntent.PriceLoaded(studentId = 2, subjectId = 7, amount = Money.ofMajor(50)),
        ).forEach { booking = booking.change(it, context).form }

        assertEquals(2L, booking.form.studentId)
        assertEquals(Money.ofMajor(50), booking.form.price.amount)
    }
}
