package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TimelineInvariantsTest {
    @Test
    fun `generated placements and slides fit inside the day and outside every busy interval`() {
        val date = LocalDate(2026, 10, 25)
        for (zone in listOf(TimeZone.UTC, TimeZone.of("Europe/Berlin"))) {
            val day = DayBounds.of(date, zone)
            for (seed in 0..39) {
                val random = Random(seed)
                val booked = (1L..12L).map { id ->
                    DayTimeline.Lesson(
                        Scheduling.Slot(day.start + random.nextInt(30, 1200).minutes, random.nextInt(5, 121)),
                        "Student $id", Assignee.Student(id), ClassMode.IN_PERSON,
                    )
                }
                val timeline = DayTimeline.of(date, zone, booked)
                repeat(30) { case ->
                    val at = day.start + random.nextInt(-60, 1560).minutes
                    val duration = random.nextInt(5, 181)
                    val context = "seed=$seed case=$case zone=$zone at=$at duration=$duration"
                    val nearest = timeline.nearestFree(at, duration)
                    val anyFit = (0..timeline.minutes - duration).any { minute ->
                        val from = timeline.start + minute.minutes
                        val until = from + duration.minutes
                        booked.none { from < it.slot.end && it.slot.start < until }
                    }
                    assertEquals(anyFit, nearest != null, context)
                    for (start in listOfNotNull(timeline.place(at, duration), nearest)) {
                        val end = start + duration.minutes
                        assertTrue(start >= timeline.start && end <= timeline.end, context)
                        for (busy in booked) {
                            assertFalse(start < busy.slot.end && busy.slot.start < end, context)
                        }
                    }
                }
            }
        }
    }
}
