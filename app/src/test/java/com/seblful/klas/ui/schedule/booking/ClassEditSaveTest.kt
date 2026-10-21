package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditSaveTest : ClassEditTestFixture() {

    @Test
    fun `a booking saves on the schedule day without choosing the date again`() = runTest(mainRule.dispatcher) {
        seedStudentWithPricedSubject()
        val scheduleDay = env.clock.today().plus(7, DateTimeUnit.DAY)
        val vm = vm(day = scheduleDay)
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime(14, 0)))
        advanceUntilIdle()
        assertTrue(vm.state.current.canSave)

        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(scheduleDay.atTime(14, 0).toInstant(zone), env.classRepo.getAll().single().start)
    }

    @Test
    fun `saving a new class persists it with the chosen fields and signals done`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val date = env.clock.today().plus(3, DateTimeUnit.DAY)
        vm.change(
            BookingIntent.Edit {
                it.copy(
                    studentId = studentId, subjectId = subjectId, date = date,
                    timeFrom = LocalTime(10, 0), durationMinutes = 90,
                    price = MoneyField("30"), mode = ClassMode.REMOTE,
                )
            },
        )
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classes().single()
        assertEquals(studentId, saved.studentId)
        assertEquals(subjectId, saved.subjectId)
        assertEquals(90, saved.durationMinutes)
        assertEquals(Money.ofMajor(30).minor, saved.priceMinor)
        assertEquals(LessonStatus.SCHEDULED, saved.status)
        assertEquals(ClassMode.REMOTE, saved.mode)
    }

    @Test
    fun `the save is launched into the application scope, not the view model's`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(
            BookingIntent.Edit {
                it.copy(
                    studentId = studentId, subjectId = subjectId,
                    date = env.clock.today().plus(3, DateTimeUnit.DAY),
                    timeFrom = LocalTime(10, 0), durationMinutes = 60, price = MoneyField("30"),
                )
            },
        )
        mainRule.appScope.cancel()

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse("no save can complete once the scope that owns writes is gone", done)
        assertTrue("and nothing was written", env.classes().isEmpty())
    }

    @Test
    fun `tapping save twice books the lesson once and refuses nothing`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        val sent = mutableListOf<ClassEditViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect(sent::add) }
        advanceUntilIdle()

        vm.fillFor(ann, subject, env.clock.today().plus(3, DateTimeUnit.DAY), LocalTime(10, 0), minutes = 60)
        advanceUntilIdle()
        var done = 0
        vm.save { done++ }
        vm.save { done++ }
        advanceUntilIdle()

        assertEquals(1, env.classes().size)
        assertEquals(1, done)
        assertEquals(emptyList<ClassEditViewModel.Event>(), sent)
    }

    @Test
    fun `a save that fails to commit is reported and does not navigate`() =
        runTest(mainRule.dispatcher) {
            val (ann, subject) = seedStudentWithPricedSubject()
            val vm = vm()
            advanceUntilIdle()
            vm.fillFor(
                ann, subject, env.clock.today().plus(2, DateTimeUnit.DAY), LocalTime(10, 0), minutes = 60,
            )
            advanceUntilIdle()

            env.transactor.failAt = env.transactor.count + 1
            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse("a failed write must not navigate away as though it had saved", done)
            assertEquals(ClassEditViewModel.Event.SAVE_FAILED, vm.events.stream.first())

            assertTrue("the failed attempt rolled back whole", env.classes().isEmpty())

            val sent = mutableListOf<ClassEditViewModel.Event>()
            backgroundScope.launch { vm.events.stream.collect(sent::add) }
            vm.save { done = true }
            advanceUntilIdle()
            assertEquals(emptyList<ClassEditViewModel.Event>(), sent)
            assertTrue("the retry saved and navigated", done)
            assertEquals(listOf(ann), env.classes().map { it.studentId })
        }

    @Test
    fun `save does nothing for an invalid form`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        var done = false
        vm.save { done = true }
        advanceUntilIdle()
        assertFalse(done)
        assertTrue(env.classes().isEmpty())
    }

    @Test
    fun `editing a completed class into the future re-plans it back to scheduled and voids the charge`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val past = env.clock.today().minus(1, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone)
            val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilliseconds(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
            )
            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            vm.change(BookingIntent.Edit { it.copy(date = env.clock.today().plus(2, DateTimeUnit.DAY)) })
            vm.save { }
            advanceUntilIdle()

            val saved = env.classes().single { it.id == id }
            assertEquals(LessonStatus.SCHEDULED, saved.status)
            assertEquals(0L, saved.chargeMinor)
            assertEquals(1, env.classes().size)
        }

    @Test
    fun `an edit saved over a lesson completed underneath the form keeps it taught and billed`() =
        runTest(mainRule.dispatcher) {
            val start = env.clock.now().minus(2.hours)
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilliseconds(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
            )
            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            env.classRepo.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)

            vm.change(BookingIntent.Edit { it.copy(price = MoneyField("45")) })
            vm.save { }
            advanceUntilIdle()

            val saved = env.classRepo.getById(id)!!
            assertEquals("still taught", LessonStatus.COMPLETED, saved.status)
            assertEquals("still there", Attended.PRESENT, saved.attended)
            assertEquals(
                "and re-billed at the price that was just set",
                Money.ofMajor(45).minor,
                saved.charge.minor,
            )
            assertEquals("still one row, not a second booking", 1, env.classes().size)
        }

    @Test
    fun `a save over a lesson deleted underneath the form writes nothing and says it is gone`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId,
                startMillis = Instant.parse("2026-06-15T07:00:00Z").toEpochMilliseconds(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
            )
            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            env.classRepo.delete(env.classRepo.getSessionFor(id)!!)

            vm.change(BookingIntent.Edit { it.copy(price = MoneyField("45")) })
            var closed = false
            vm.save { closed = true }
            advanceUntilIdle()

            assertFalse("the form did not close as though it saved", closed)
            assertTrue(vm.gone.value)
            assertTrue("nothing was booked in its place", env.classRepo.getAll().isEmpty())
        }

    @Test
    fun `editing a completed class in the past rebills it from the new price and duration`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            // A fixed 10:00 rather than a week ago exactly: this test lengthens the lesson to 90 minutes,
            // which — while the form held an end time — pushed it past midnight on an evening run and made
            // the charge assertion fail for the time of day rather than for the rule.
            val past = env.clock.today().minus(7, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone)
            val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilliseconds(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
            )
            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            // Keep it in the past (its own date), change price to 40/h and duration to 90 min → charge 60.
            val pastDate = past.toLocalDateTime(zone).date
            val from = past.toLocalDateTime(zone).time
            vm.change(
                BookingIntent.Edit {
                    it.copy(date = pastDate, timeFrom = from, durationMinutes = 90, price = MoneyField("40"))
                },
            )
            vm.save { }
            advanceUntilIdle()

            val saved = env.classes().single { it.id == id }
            assertEquals(LessonStatus.COMPLETED, saved.status)
            assertEquals(Money.ofMajor(60).minor, saved.chargeMinor)
        }
}
