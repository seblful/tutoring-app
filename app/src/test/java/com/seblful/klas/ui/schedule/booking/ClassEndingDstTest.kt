package com.seblful.klas.ui.schedule.booking

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassEndingDstTest {
    @Test
    fun `the displayed ending agrees with the reserved slot across both DST transitions`() {
        val zone = TimeZone.of("Europe/Berlin")
        listOf(LocalDate(2026, 3, 29), LocalDate(2026, 10, 25)).forEach { date ->
            val form = ClassForm(date = date, timeFrom = LocalTime(1, 30), durationMinutes = 120)
            val end = form.slot(zone)!!.end.toLocalDateTime(zone)

            assertEquals(end.time, form.ends(zone)?.time)
            assertEquals(end.date > date, form.ends(zone)?.nextDay)
        }
    }
}
