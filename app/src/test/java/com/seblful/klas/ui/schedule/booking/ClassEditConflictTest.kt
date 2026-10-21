package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditConflictTest : ClassEditTestFixture() {

    @Test
    fun `a cancelled past lesson remains editable after its slot is reused`() = runTest(mainRule.dispatcher) {
        val date = env.clock.today().minus(2, DateTimeUnit.DAY)
        val (_, id) = seedLesson(date, LocalTime(10, 0))
        env.classRepo.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.CANCELLED)
        seedLesson(date, LocalTime(10, 0), studentName = "Replacement")
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()
        vm.change(BookingIntent.Edit { it.copy(mode = ClassMode.REMOTE) })
        advanceUntilIdle()

        assertNull(vm.state.current.conflict)
        assertTrue(vm.state.current.canSave)
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(ClassMode.REMOTE, env.classRepo.getById(id)!!.mode)
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getById(id)!!.status)
    }

    private fun TestScope.clashesDrawnBy(vm: ClassEditViewModel): List<TimeConflict> {
        val drawn = mutableListOf<TimeConflict>()
        backgroundScope.launch {
            vm.state.collect { (it as? Loadable.Ready)?.value?.conflict?.let(drawn::add) }
        }
        return drawn
    }

    @Test
    fun `a lesson cannot be booked over one that is already there`() = runTest(mainRule.dispatcher) {
        val date = env.clock.today().plus(3, DateTimeUnit.DAY)
        seedLesson(date, LocalTime(10, 0)) // Борис, 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // Аня at 10:30 would overlap Борис's lesson — the tutor cannot teach both.
        vm.fillFor(annId, subjectId, date, LocalTime(10, 30), minutes = 60)
        advanceUntilIdle()

        val clash = vm.state.current.conflict
        assertEquals("Борис", clash?.who)
        assertEquals(date.atTime(10, 0).toInstant(zone), clash?.slot?.start)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse("the save must not go through", done)
        assertEquals("still only the lesson that was there", 1, env.classes().size)
        assertEquals("a refused save leaves the clash named", "Борис", vm.state.current.conflict?.who)
    }

    @Test
    fun `a lesson starting exactly when another ends is allowed`() = runTest(mainRule.dispatcher) {
        val date = env.clock.today().plus(3, DateTimeUnit.DAY)
        seedLesson(date, LocalTime(10, 0)) // 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime(11, 0), minutes = 60)
        advanceUntilIdle()
        assertNull(vm.state.current.conflict)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(2, env.classes().size)
    }

    @Test
    fun `a cancelled lesson frees its hour`() = runTest(mainRule.dispatcher) {
        val date = env.clock.today().plus(3, DateTimeUnit.DAY)
        val (_, bookedId) = seedLesson(date, LocalTime(10, 0))
        env.classRepo.setStatus(env.classRepo.getSessionFor(bookedId)!!, LessonStatus.CANCELLED)
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime(10, 0), minutes = 60)
        advanceUntilIdle()

        assertNull("a cancelled lesson is not happening, so the slot is free", vm.state.current.conflict)
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classes().size)
    }

    @Test
    fun `re-saving the lesson being edited does not clash with itself`() = runTest(mainRule.dispatcher) {
        val date = env.clock.today().plus(3, DateTimeUnit.DAY)
        val (_, bookedId) = seedLesson(date, LocalTime(10, 0))

        val vm = vm(bookedId)
        observing(vm.state)
        advanceUntilIdle()

        assertNull("its own hour is not a conflict", vm.state.current.conflict)

        vm.change(BookingIntent.Edit { it.copy(durationMinutes = 90) })
        advanceUntilIdle()
        assertNull(vm.state.current.conflict)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(90, env.classes().single().durationMinutes)
    }

    @Test
    fun `a group lesson does not clash with its own members when re-saved`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = env.clock.today().plus(3, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone)
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.current.conflict)
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(2, env.classes().size)
    }

    @Test
    fun `a lesson just booked is never shown clashing with itself`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        val clashes = clashesDrawnBy(vm)
        advanceUntilIdle()

        vm.fillFor(ann, subject, env.clock.today().plus(3, DateTimeUnit.DAY), LocalTime(10, 0), minutes = 60)
        advanceUntilIdle()
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(1, env.classes().size)
        assertEquals("no clash was drawn at any point", emptyList<TimeConflict>(), clashes)
    }

    @Test
    fun `a member ticked into a booked lesson is not shown clashing with it once saved`() =
        runTest(mainRule.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val ids = env.classRepo.book(
                groupId = groupId, subjectId = env.stored().subjects.first().id,
                start = env.clock.today().plus(3, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone),
                durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(ann),
                price = groupRate,
            ).written()

            val vm = vm(id = ids.first())
            val clashes = clashesDrawnBy(vm)
            advanceUntilIdle()

            vm.change(BookingIntent.IncludeMember(bob, true))
            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertTrue(done)
            assertEquals(2, env.classes().size)
            assertEquals("no clash was drawn at any point", emptyList<TimeConflict>(), clashes)
        }

    @Test
    fun `a group lesson in the way is reported by its group name`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val date = env.clock.today().plus(4, DateTimeUnit.DAY)
        env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id,
            start = date.atTime(10, 0).toInstant(zone),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()
        val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(carl, physics, date, LocalTime(10, 30), minutes = 60)
        advanceUntilIdle()

        assertEquals("11 «А»", vm.state.current.conflict?.who)
        vm.save { }
        advanceUntilIdle()
        assertEquals("no third class was written", 2, env.classes().size)
    }

    @Test
    fun `a save refused for a taken slot says so instead of doing nothing`() =
        runTest(mainRule.dispatcher) {
            val (occupier, subject) = seedStudentWithPricedSubject(studentName = "Борис")
            val date = env.clock.today().plus(3, DateTimeUnit.DAY)
            env.classRepo.book(
                subjectId = subject, start = date.atTime(10, 0).toInstant(zone),
                durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(occupier), price = Money.ofMajor(30),
            )
            val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

            val vm = vm()
            advanceUntilIdle()
            vm.fillFor(carl, physics, date, LocalTime(10, 0), minutes = 60)
            advanceUntilIdle()

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse("nothing was written", done)
            assertEquals(1, env.classes().size)
            assertEquals(
                ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN,
                vm.events.stream.first(),
            )
        }

    @Test
    fun `a single booking onto a taken hour still says that hour is taken`() =
        runTest(mainRule.dispatcher) {
            // The one-lesson case goes through the same write now, and its wording must not drift into
            // the run's.
            val date = env.clock.today().plus(3, DateTimeUnit.DAY)
            seedLesson(date, LocalTime(10, 0))
            val (ann, subject) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()
            vm.fillFor(ann, subject, date, LocalTime(10, 0), minutes = 60)
            vm.save { }
            advanceUntilIdle()

            assertEquals(
                ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN,
                vm.events.stream.first(),
            )
        }
}
