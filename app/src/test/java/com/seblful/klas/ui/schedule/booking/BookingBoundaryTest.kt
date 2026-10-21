package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.schedule.booking.ClassForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingBoundaryTest {

    @Test
    fun `a rate the money field accepts never bills a negative charge`() {
        val absurd = MoneyField(text = "2000000000000000", kind = MoneyField.Kind.RATE)
        assertFalse("an amount that would wrap the charge is not an amount", absurd.isValid)

        val charge = ClassRules.chargeFor(Money.MAX, durationMinutes = 24 * 60)
        assertFalse("the largest accepted rate cannot bill a negative amount", charge.isNegative)
    }

    @Test
    fun `a lesson may cross midnight`() {
        val form = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 3, 2),
            timeFrom = LocalTime(23, 30),
            durationMinutes = 60,
            price = MoneyField(text = "30"),
        )
        assertTrue(form.isValid)
        assertEquals(LocalTime(0, 30), form.ends(TimeZone.of("Europe/Minsk"))?.time)
        assertEquals(true, form.ends(TimeZone.of("Europe/Minsk"))?.nextDay)

        val slot = form.slot(TimeZone.of("Europe/Minsk"))!!
        assertEquals(
            "the lesson ends on the 3rd, an hour after it started on the 2nd",
            LocalDateTime(2026, 3, 3, 0, 30),
            slot.end.toLocalDateTime(TimeZone.of("Europe/Minsk")),
        )
    }
}
