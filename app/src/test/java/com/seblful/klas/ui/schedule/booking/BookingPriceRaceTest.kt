package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingPriceRaceTest {
    private fun BookingForm.after(vararg intents: BookingIntent): BookingForm =
        intents.fold(this) { booking, intent -> booking.change(intent, BookingContext()).form }

    @Test
    fun `changing subjects replaces the automatic rate and rejects a late old suggestion`() {
        val booking = BookingForm().after(
            BookingIntent.ChooseStudent(1),
            BookingIntent.ChooseSubject(7),
            BookingIntent.PriceLoaded(1, 7, Money.ofMajor(30)),
            BookingIntent.ChooseSubject(8),
            BookingIntent.PriceLoaded(1, 7, Money.ofMajor(30)),
            BookingIntent.PriceLoaded(1, 8, Money.ofMajor(45)),
        )
        assertEquals(Money.ofMajor(45), booking.form.price.amount)
    }

    @Test
    fun `changing subjects preserves a deliberate override including a cleared field`() {
        for (text in listOf("35", "")) {
            val booking = BookingForm().after(
                BookingIntent.ChooseStudent(1),
                BookingIntent.ChooseSubject(7),
                BookingIntent.PriceLoaded(1, 7, Money.ofMajor(30)),
                BookingIntent.Edit { it.copy(price = it.price.typed(text)) },
                BookingIntent.ChooseSubject(8),
                BookingIntent.PriceLoaded(1, 8, Money.ofMajor(45)),
            )
            assertEquals(text, booking.form.price.text)
        }
    }

    @Test
    fun `an existing booking keeps its historical rate when its subject changes`() {
        val booking = BookingForm().after(
            BookingIntent.Load(ClassForm(studentId = 1, subjectId = 7, price = MoneyField.of(Money.ofMajor(25)))),
            BookingIntent.ChooseSubject(8),
            BookingIntent.PriceLoaded(1, 8, Money.ofMajor(45)),
        )
        assertEquals(Money.ofMajor(25), booking.form.price.amount)
    }

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
