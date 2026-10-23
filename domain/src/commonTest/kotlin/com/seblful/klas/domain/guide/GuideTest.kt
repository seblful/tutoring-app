package com.seblful.klas.domain.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideTest {

    @Test
    fun `the schedule leads with the swipe while a lesson can take it`() {
        assertEquals(Tip.LESSON_SWIPE, Tip.forSchedule(emptySet(), swipeable = true, lessons = 3))
    }

    @Test
    fun `with nothing to swipe - picking once there are two lessons`() {
        assertEquals(Tip.LESSON_PICK, Tip.forSchedule(emptySet(), swipeable = false, lessons = 2))
    }

    @Test
    fun `an empty schedule still names its date title`() {
        assertEquals(Tip.SCHEDULE_DATE, Tip.forSchedule(emptySet(), swipeable = false, lessons = 0))
    }

    @Test
    fun `one lesson is not enough to pick`() {
        assertEquals(Tip.SCHEDULE_DATE, Tip.forSchedule(setOf(Tip.LESSON_SWIPE), swipeable = true, lessons = 1))
    }

    @Test
    fun `a seen tip gives way to the next`() {
        val seen = setOf(Tip.LESSON_SWIPE, Tip.LESSON_PICK)
        assertEquals(Tip.SCHEDULE_DATE, Tip.forSchedule(seen, swipeable = true, lessons = 4))
    }

    @Test
    fun `once all are seen the schedule shows none`() {
        assertNull(Tip.forSchedule(Tip.entries.toSet(), swipeable = true, lessons = 4))
    }

    @Test
    fun `homework's tip waits for homework`() {
        assertNull(Tip.forHomework(emptySet(), current = 0))
        assertEquals(Tip.HOMEWORK_SWIPE, Tip.forHomework(emptySet(), current = 1))
        assertNull(Tip.forHomework(setOf(Tip.HOMEWORK_SWIPE), current = 1))
    }

    @Test
    fun `done means every tip seen and the card put away`() {
        assertEquals(Tip.entries.toSet(), Guide.DONE.seen)
        assertTrue(Guide.DONE.firstStepsHidden)
        assertFalse(Guide.FRESH.firstStepsHidden)
        assertEquals(setOf(Tip.HOMEWORK_SWIPE), Guide.FRESH.seeing(Tip.HOMEWORK_SWIPE).seen)
    }

    @Test
    fun `steps are ticked by what has happened`() {
        val steps = FirstSteps.of(FirstSteps.Progress(booked = true, paid = true), reminded = true)
        assertEquals(setOf(FirstStep.BOOK, FirstStep.PAYMENT, FirstStep.REMINDERS), steps.done)
        assertEquals(FirstStep.MARK_TAUGHT, steps.next)
        assertFalse(steps.complete)
    }

    @Test
    fun `every deed done completes the steps`() {
        val everything = FirstSteps.Progress(booked = true, taught = true, paid = true, homework = true)
        val steps = FirstSteps.of(everything, reminded = true)
        assertTrue(steps.complete)
        assertNull(steps.next)
    }

    @Test
    fun `reminders that do not reach the phone are not done`() {
        val everything = FirstSteps.Progress(booked = true, taught = true, paid = true, homework = true)
        val steps = FirstSteps.of(everything, reminded = false)
        assertFalse(steps.complete)
        assertEquals(FirstStep.REMINDERS, steps.next)
    }
}
