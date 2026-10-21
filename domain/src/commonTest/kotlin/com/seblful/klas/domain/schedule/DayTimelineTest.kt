package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DayTimelineTest {

    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 10, 22)

    private fun at(hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).toInstant(utc)

    private fun booked(hour: Int, minute: Int, minutes: Int, who: String = "Мария") =
        DayTimeline.Lesson(Scheduling.Slot(at(hour, minute), minutes), who, Assignee.Student(1), ClassMode.IN_PERSON)

    // 9:00–23:00 with Мария 15:30–16:30 and the group 18:00–19:30.
    private val thursday = DayTimeline.of(day, utc, listOf(booked(15, 30, 60), booked(18, 0, 90, who = "11 «А»")))

    @Test
    fun `every day is drawn from nine to eleven`() {
        assertEquals(at(9), thursday.start)
        assertEquals(at(23), thursday.end)
        assertEquals(840, thursday.minutes)
    }

    @Test
    fun `a lesson outside those hours widens the timeline to whole hours around it`() {
        val timeline = DayTimeline.of(day, utc, listOf(booked(7, 30, 45), booked(22, 30, 60)))

        assertEquals(at(7), timeline.start)
        assertEquals(timeline.day.end, timeline.end)
    }

    @Test
    fun `the lesson being booked widens the timeline but never past the day`() {
        val late = thursday.including(Scheduling.Slot(at(23, 0), 120))

        assertEquals(at(9), late.start)
        assertEquals(late.day.end, late.end)
    }

    @Test
    fun `a lesson on another day leaves the timeline as it is`() {
        val elsewhere = Scheduling.Slot(LocalDate(2026, 10, 23).atTime(9, 0).toInstant(utc), 60)

        assertEquals(thursday, thursday.including(elsewhere))
    }

    @Test
    fun `a lesson clashes only with the day's lessons it runs into`() {
        assertTrue(thursday.clashesWith(Scheduling.Slot(at(15, 0), 60)))
        assertFalse(thursday.clashesWith(Scheduling.Slot(at(16, 30), 90)))
    }

    @Test
    fun `a tap starts the lesson at the five minutes tapped`() {
        assertEquals(at(13, 45), thursday.place(at(13, 47), minutes = 60))
    }

    @Test
    fun `a tap just before a lesson books back to back with it`() {
        // 15:00 for an hour would run into Мария at 15:30, so it ends as she starts.
        assertEquals(at(14, 30), thursday.place(at(15, 10), minutes = 60))
    }

    @Test
    fun `a tap just after a lesson starts as it ends`() {
        val timeline = DayTimeline.of(day, utc, listOf(booked(14, 0, 73)))

        // The step tapped is 15:10, still inside the lesson ending at 15:13.
        assertEquals(at(15, 13), timeline.place(at(15, 14), minutes = 60))
    }

    @Test
    fun `a tap on a booked lesson places nothing`() {
        assertNull(thursday.place(at(15, 45), minutes = 60))
    }

    @Test
    fun `a gap shorter than the lesson places nothing`() {
        // 16:30–18:00 holds an hour and a half, not two.
        assertNull(thursday.place(at(17, 0), minutes = 120))
        assertEquals(at(16, 30), thursday.place(at(17, 0), minutes = 90))
    }

    @Test
    fun `a tap outside the timeline places nothing`() {
        assertNull(thursday.place(at(23, 30), minutes = 60))
    }

    @Test
    fun `the first free time skips the day's lessons`() {
        // From 13:00 there are 2.5 hours before Мария, three only after the group, and four nowhere.
        assertEquals(at(13), thursday.firstFree(minutes = 60, notBefore = at(13)))
        assertEquals(at(19, 30), thursday.firstFree(minutes = 180, notBefore = at(13)))
        assertNull(thursday.firstFree(minutes = 240, notBefore = at(13)))
        assertEquals(at(19, 30), thursday.firstFree(minutes = 90, notBefore = at(17, 10)))
    }

    @Test
    fun `the first free time is never in the past`() {
        assertEquals(at(14, 10), thursday.firstFree(minutes = 60, notBefore = at(14, 7)))
    }

    @Test
    fun `a slide lands on the nearest five minutes`() {
        assertEquals(at(10, 10), thursday.nearestFree(at(10, 8), minutes = 60))
        assertEquals(at(10, 5), thursday.nearestFree(at(10, 7), minutes = 60))
    }

    @Test
    fun `a slide into a lesson hops to its nearer side`() {
        // Мария is 15:30–16:30: an hour fits just before her or just after.
        assertEquals(at(14, 30), thursday.nearestFree(at(15, 10), minutes = 60))
        assertEquals(at(16, 30), thursday.nearestFree(at(15, 50), minutes = 60))
    }

    @Test
    fun `a slide passes over a gap too short for the lesson`() {
        // 16:30–18:00 holds 90 minutes: two hours go on to 19:30, nearer than 13:30 before Мария.
        assertEquals(at(19, 30), thursday.nearestFree(at(16, 40), minutes = 120))
        assertNull(thursday.nearestFree(at(12), minutes = 13 * 60))
    }

    @Test
    fun `lessons inside one another are one busy stretch to a slide`() {
        val timeline = DayTimeline.of(day, utc, listOf(booked(10, 0, 240), booked(11, 0, 60)))

        assertEquals(at(14), timeline.nearestFree(at(12, 30), minutes = 60))
    }

    @Test
    fun `a step moves a lesson five minutes in free time`() {
        assertEquals(at(12, 5), thursday.shifted(at(12), 5.minutes, minutes = 60))
        assertEquals(at(11, 55), thursday.shifted(at(12), (-5).minutes, minutes = 60))
    }

    @Test
    fun `a step from back to back with a lesson goes past it`() {
        // 14:30 ends as Мария starts at 15:30; later is after her, and from 16:30 earlier is before her.
        assertEquals(at(16, 30), thursday.shifted(at(14, 30), 5.minutes, minutes = 60))
        assertEquals(at(14, 30), thursday.shifted(at(16, 30), (-5).minutes, minutes = 60))
    }

    @Test
    fun `a step passes over a gap too short for the lesson`() {
        // Two hours fit after Мария only past the group, and from 9:00 there is nowhere earlier.
        assertEquals(at(19, 30), thursday.shifted(at(13, 30), 5.minutes, minutes = 120))
        assertNull(thursday.shifted(at(9), (-5).minutes, minutes = 60))
    }

    @Test
    fun `a tap on a lesson finds it, a tap beside it does not`() {
        assertEquals("Мария", thursday.lessonAt(at(15, 45))?.who)
        assertEquals("11 «А»", thursday.lessonAt(at(18, 0))?.who)
        assertNull(thursday.lessonAt(at(16, 30)))
    }

    @Test
    fun `a group lesson is the group's, an individual one the student's`() {
        val session = { groupId: Long? ->
            ClassSession(
                groupId = groupId,
                classes = listOf(
                    TutoringClass(
                        studentId = 7,
                        subjectId = 1,
                        start = at(10),
                        durationMinutes = 60,
                        mode = ClassMode.REMOTE,
                    ),
                ),
            )
        }

        val group = DayTimeline.Lesson.of(session(3), groupName = "11 «А»", studentName = "Мария")
        val single = DayTimeline.Lesson.of(session(null), groupName = null, studentName = "Мария")

        assertEquals(Assignee.Group(3), group.assignee)
        assertEquals("11 «А»", group.who)
        assertEquals(Assignee.Student(7), single.assignee)
        assertEquals("Мария", single.who)
        assertEquals(ClassMode.REMOTE, single.mode)
        assertEquals(Scheduling.Slot(at(10), 60), single.slot)
    }

    @Test
    fun `a day the clocks go back on draws the extra hour`() {
        val berlin = TimeZone.of("Europe/Berlin")
        val fallBack = LocalDate(2026, 10, 25)
        val oneAm = fallBack.atTime(1, 0).toInstant(berlin)

        val early = DayTimeline.Lesson(Scheduling.Slot(oneAm, 60), "Мария", Assignee.Student(1), ClassMode.IN_PERSON)
        val timeline = DayTimeline.of(fallBack, berlin, listOf(early))

        // 1:00 to 23:00 by the clock, and the hour 2:00–3:00 happens twice.
        assertEquals(oneAm, timeline.start)
        assertEquals(23 * 60, timeline.minutes)
    }
}
