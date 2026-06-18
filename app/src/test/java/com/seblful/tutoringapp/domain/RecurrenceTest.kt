package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecurrenceTest {

    private val zone = ZoneId.of("Europe/Minsk")
    private val first = TutoringClass(
        studentId = 1, subjectId = 1,
        start = Instant.parse("2026-06-18T14:00:00Z"), durationMinutes = 60,
    )

    @Test
    fun `generates the requested number of weekly occurrences`() {
        val series = Recurrence.weekly(first, zone, weeks = 4, groupId = 7)
        assertEquals(4, series.size)
        assertTrue(series.all { it.recurringGroupId == 7L })
    }

    @Test
    fun `occurrences are exactly one week apart in wall-clock time`() {
        val series = Recurrence.weekly(first, zone, weeks = 3, groupId = 1)
        val localTimes = series.map { it.start.atZone(zone).toLocalTime() }
        assertTrue(localTimes.all { it == localTimes.first() }) // same local time each week
        assertEquals(first.start, series.first().start)
        assertEquals(
            first.start.atZone(zone).plusWeeks(2).toInstant(),
            series[2].start,
        )
    }
}
