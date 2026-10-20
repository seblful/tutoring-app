package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingDraftTest {

    @Test
    fun `a draft carries every choice and whether the tutor made it`() {
        val booking = BookingForm(
            form = ClassForm(
                target = ClassTarget.GROUP,
                groupId = 9,
                subjectId = 3,
                date = LocalDate(2026, 10, 22),
                timeFrom = LocalTime(16, 30),
                durationMinutes = 90,
                price = MoneyField("40,50"),
                mode = ClassMode.REMOTE,
                excludedMemberIds = setOf(4, 5),
                repeat = Repeat.WEEKLY,
                repeatCount = 8,
            ),
            touched = true,
            automaticPrice = false,
            filledFrom = LocalDate(2026, 10, 15),
        )

        assertEquals(booking, BookingDraft.decode(BookingDraft.encode(booking)))
    }

    @Test
    fun `an empty booking survives the trip too`() {
        val booking = BookingForm()

        assertEquals(booking, BookingDraft.decode(BookingDraft.encode(booking)))
    }
}
