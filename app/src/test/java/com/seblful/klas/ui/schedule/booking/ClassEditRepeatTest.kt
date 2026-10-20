package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditRepeatTest : ClassEditTestFixture() {

    private fun ClassEditViewModel.fillRepeating(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        from: LocalTime,
        every: Repeat,
        count: Int,
    ) = change(
        BookingIntent.Edit {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = from, durationMinutes = 60, price = MoneyField("30"),
                repeat = every, repeatCount = count,
            )
        },
    )

    @Test
    fun `a form that does not repeat books exactly one lesson`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(
            ann, subject, Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY), LocalTime(10, 0), minutes = 60,
        )
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(1, env.classRepo.getAll().size)
        assertNull("nothing to report about a single booking", vm.seriesOutcome.value)
    }

    @Test
    fun `a weekly run books a lesson a week and navigates back`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val first = Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY)
        vm.fillRepeating(ann, subject, first, LocalTime(10, 0), Repeat.WEEKLY, count = 4)
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue("a run that got every date it asked for is an ordinary save", done)
        assertNull(vm.seriesOutcome.value)
        assertEquals(
            (0..3).map { first.plus(it, DateTimeUnit.WEEK) },
            env.classRepo.getAll().map { it.start.toLocalDateTime(zone).date }.sorted(),
        )
    }

    @Test
    fun `the form says how far a run reaches before it is saved`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val first = Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY)
        vm.fillRepeating(ann, subject, first, LocalTime(10, 0), Repeat.FORTNIGHTLY, count = 3)
        advanceUntilIdle()

        // «по 24 сентября» — read off the same rule the write uses, so the promise on screen and the
        // lessons written cannot differ.
        assertEquals(first.plus(4, DateTimeUnit.WEEK), vm.state.current.form.repeatsUntil)
    }

    @Test
    fun `a lesson loaded for editing never repeats`() = runTest(mainRule.dispatcher) {
        // Repeating is a property of the act of booking; there is nothing for it to say about a row
        // already in the database, and an edit that quietly turned one lesson into twelve would be the
        // worst possible reading of the field.
        val (_, id) = seedLesson(Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY), LocalTime(14, 0))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Repeat.NONE, vm.state.current.form.repeat)
        assertNull(vm.state.current.form.repeatsUntil)
    }

    @Test
    fun `a run that loses one date books the rest and holds the screen to say so`() =
        runTest(mainRule.dispatcher) {
            val first = Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY)
            // Борис already has the third Monday of the run.
            seedLesson(first.plus(2, DateTimeUnit.WEEK), LocalTime(10, 0))
            val (ann, subject) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()
            vm.fillRepeating(ann, subject, first, LocalTime(10, 0), Repeat.WEEKLY, count = 4)

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse("the screen waits for the report to be read", done)
            val outcome = vm.seriesOutcome.value!!
            assertEquals(3, outcome.booked)
            assertEquals(listOf(first.plus(2, DateTimeUnit.WEEK)), outcome.skipped)
        }

    @Test
    fun `acknowledging the report clears it, and leaving then asks nothing`() =
        runTest(mainRule.dispatcher) {
            val first = Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY)
            seedLesson(first.plus(1, DateTimeUnit.WEEK), LocalTime(10, 0))
            val (ann, subject) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()
            vm.fillRepeating(ann, subject, first, LocalTime(10, 0), Repeat.WEEKLY, count = 3)
            vm.save { }
            advanceUntilIdle()

            assertNotNull(vm.seriesOutcome.value)
            vm.acknowledgeSeries()
            advanceUntilIdle()

            assertNull(vm.seriesOutcome.value)
            // The lessons are in the database, so back must not go on offering to save them.
            assertFalse(vm.state.current.isDirty)
        }

    @Test
    fun `a run whose every date is taken writes nothing and says which refusal it was`() =
        runTest(mainRule.dispatcher) {
            val first = Clock.System.todayIn(zone).plus(2, DateTimeUnit.DAY)
            val (bob, physics) = seedStudentWithPricedSubject(studentName = "Борис", subjectName = "Химия")
            repeat(3) { week ->
                env.classRepo.book(
                    subjectId = physics,
                    start = first.plus(week.toLong(), DateTimeUnit.WEEK).atTime(10, 0).toInstant(zone),
                    durationMinutes = 60,
                    mode = ClassMode.IN_PERSON,
                    studentIds = listOf(bob),
                    price = Money.ofMajor(30),
                ).written()
            }
            val (ann, subject) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()
            vm.fillRepeating(ann, subject, first, LocalTime(10, 0), Repeat.WEEKLY, count = 3)

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse(done)
            assertNull("nothing was written, so there is no report — it is a refusal", vm.seriesOutcome.value)
            // Worded for a run rather than for the one time on the form: naming three dates the tutor
            // cannot see would be a claim they have no way to check.
            assertEquals(
                ClassEditViewModel.Event.SERIES_REFUSED_ALL_TAKEN,
                vm.events.stream.first(),
            )
            assertEquals(3, env.classRepo.getAll().size)
        }
}
