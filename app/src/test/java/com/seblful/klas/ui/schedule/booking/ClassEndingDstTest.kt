package com.seblful.klas.ui.schedule.booking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ClassEndingDstTest {
    @Test
    fun `the displayed ending agrees with the reserved slot across both DST transitions`() {
        val zone = ZoneId.of("Europe/Berlin")
        listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25)).forEach { date ->
            val form = ClassForm(date = date, timeFrom = LocalTime.of(1, 30), durationMinutes = 120)
            val end = form.slot(zone)!!.end.atZone(zone)

            assertEquals(end.toLocalTime(), form.ends(zone)?.time)
            assertEquals(end.toLocalDate().isAfter(date), form.ends(zone)?.nextDay)
        }
    }
}
