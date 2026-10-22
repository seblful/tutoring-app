package com.seblful.klas.ui.students

import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.queries.students.ArchiveIntent
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudentArchiveTest : StudentDetailTestFixture() {

    @Test
    fun `archiving and restoring a student is reflected in the entity`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.setArchived(true)
        runCurrent()
        assertTrue(env.studentDao.getById(sid)!!.archived)

        vm.setArchived(false)
        runCurrent()
        assertFalse(env.studentDao.getById(sid)!!.archived)
    }

    @Test
    fun `archiving a student who owes nothing and has no lessons booked asks nothing`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            runCurrent()

            assertEquals(ArchiveIntent.Archive, vm.state.current.archiveIntent)
        }

    @Test
    fun `the archive question names the balance, the planned lessons, or both`() =
        runTest(mainRule.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val sid = env.fixtures.pricedStudent("Аня", subject, Money.ofMajor(30))
            env.fixtures.completedLesson(
                subject, listOf(sid), today.minus(2, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone),
            )
            env.fixtures.lesson(subject, listOf(sid), today.plus(2, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone))
            val vm = vm(sid)
            observing(vm.state)
            runCurrent()

            val both = vm.state.current.archiveIntent as ArchiveIntent.Confirm
            assertEquals(Balance.Owed(Money.ofMajor(30)), both.openBalance)
            assertEquals(1, both.upcomingLessons)

            vm.addPayment(Money.ofMajor(30), today)
            runCurrent()
            val lessonsOnly = vm.state.current.archiveIntent as ArchiveIntent.Confirm
            assertNull("nothing outstanding to mention", lessonsOnly.openBalance)
            assertEquals(1, lessonsOnly.upcomingLessons)
        }

    @Test
    fun `an over-payment is worth asking about too`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.addPayment(Money.ofMajor(25), today)
        runCurrent()

        val confirm = vm.state.current.archiveIntent as ArchiveIntent.Confirm
        assertEquals(Balance.Credit(Money.ofMajor(25)), confirm.openBalance)
        assertEquals(0, confirm.upcomingLessons)
    }

    @Test
    fun `restoring an archived student asks nothing even with a debt`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subject, Money.ofMajor(30))
        env.fixtures.completedLesson(
            subject, listOf(sid), today.minus(2, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone),
        )
        env.studentRepo.setArchived(env.studentRepo.getById(sid)!!, archived = true)
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        assertEquals(ArchiveIntent.Restore, vm.state.current.archiveIntent)
    }

    @Test
    fun `archiving calls off the student's planned lessons`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.fixtures.subject(name = "Алгебра")
        val future = env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )
        // A past lesson still merely planned is the one auto-complete is about to bill.
        val pastPlanned = env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = 1_000,
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )
        val done = env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = 2_000,
            durationMinutes = 60, status = LessonStatus.COMPLETED, priceMinor = 3000, chargeMinor = 3000,
        )
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.setArchived(true)
        runCurrent()

        val byId = env.classes().associateBy { it.id }
        assertEquals(LessonStatus.CANCELLED, byId.getValue(future).status)
        assertEquals(
            "a past lesson nobody confirmed is not about to become income",
            LessonStatus.CANCELLED,
            byId.getValue(pastPlanned).status,
        )
        assertEquals("what actually happened is history and stays", LessonStatus.COMPLETED, byId.getValue(done).status)
        assertEquals("its income stays too", 3000, byId.getValue(done).chargeMinor)
        assertTrue(env.studentDao.getById(sid)!!.archived)
    }

    @Test
    fun `archiving says how many lessons it called off, and undo plans them again`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.fixtures.subject(name = "Алгебра")
        env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setArchived(true)
        runCurrent()

        val archived = events.single() as StudentDetailViewModel.Event.Archived
        assertEquals(1, archived.cancelled.size)

        vm.undoArchive(archived)
        runCurrent()

        assertFalse(env.studentDao.getById(sid)!!.archived)
        assertEquals(LessonStatus.SCHEDULED, env.classes().single().status)
        assertEquals("nothing stayed cancelled, so nothing more is said", 1, events.size)
    }

    @Test
    fun `archiving a student with nothing planned is still said, with its undo`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setArchived(true)
        runCurrent()

        assertEquals(listOf(StudentDetailViewModel.Event.Archived(cancelled = emptyList())), events)
        assertTrue(env.studentDao.getById(sid)!!.archived)
    }

    /** Restoring is not an undo: a lesson that was called off is a fact the tutor can re-book by hand. */
    @Test
    fun `restoring does not put the called-off lessons back`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.fixtures.subject(name = "Алгебра")
        env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.setArchived(true)
        runCurrent()
        vm.setArchived(false)
        runCurrent()

        assertFalse(env.studentDao.getById(sid)!!.archived)
        assertEquals(LessonStatus.CANCELLED, env.classes().single().status)
    }

    /** The freed hour really is free: a cancelled class gives its slot back. */
    @Test
    fun `the hours of a called-off lesson become bookable again`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.fixtures.subject(name = "Алгебра")
        val at = 4_000_000_000_000
        env.fixtures.storedClass(
            studentId = sid, subjectId = subject, startMillis = at,
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.setArchived(true)
        runCurrent()

        val slot = Scheduling.Slot(Instant.fromEpochMilliseconds(at), 60)
        assertTrue(
            "the cancelled lesson stands in nobody's way",
            env.classRepo.conflictsWith(slot).isEmpty(),
        )
    }
}
