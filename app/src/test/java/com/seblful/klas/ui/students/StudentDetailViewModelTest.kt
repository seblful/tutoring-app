package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    // A pinned clock, because the hour a payment lands at is derived from "today" — see `PaymentDate`.
    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val today: LocalDate = LocalDate.of(2026, 5, 17)
    private val clock = MutableClock(today.atTime(14, 30).atZone(zone).toInstant(), zone)

    private fun vm(id: Long) = StudentDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        students = env.studentRepo,
        subjects = env.subjectRepo,
        classes = env.classRepo,
        groups = env.groupRepo,
        payments = env.paymentRepo,
        enrollment = env.enrollmentRepo,
        rateHistory = env.rateHistoryRepo,
        clock = clock,
        appScope = mainRule.appScope,
        computation = mainRule.dispatcher,
    )

    private suspend fun seedStudent(name: String = "Аня"): Long =
        env.studentDao.upsert(StudentEntity(name = name))

    @Test
    fun `balance is completed charges minus payments`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // Two completed classes (30 + 45) and one cancelled (ignored), minus a 50 payment.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90, status = "COMPLETED", chargeMinor = Money.ofMajor(45).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "CANCELLED", chargeMinor = 0))
        env.paymentRepo.add(Payment(studentId = sid, date = Instant.ofEpochMilli(10), amount = Money.ofMajor(50)))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(25), vm.state.value.balance)
    }

    @Test
    fun `subjects shown are exactly the priced ones, sorted by name`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.subjectDao.upsert(SubjectEntity(name = "Физика")) // exists but unpriced for this student
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(sid, algebra, Money.ofMajor(20).minor))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf("Алгебра", "Математика"), vm.state.value.subjects.map { it.subject.name })
    }

    @Test
    fun `the stat row counts completed lessons, their hours and their income`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90, status = "COMPLETED", chargeMinor = Money.ofMajor(45).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        val totals = vm.state.value.totals
        assertEquals(2, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals("a scheduled lesson adds no hours yet", 150, totals.minutes)
        assertEquals(Money.ofMajor(75), totals.earned)
    }

    /**
     * A student's lesson list is the one place the two kinds of teaching mix, and they are billed at
     * different rates — the group's, and the student's own — so a row has to say which it was. The
     * name matters more than a flag: *which* group is what explains the amount.
     */
    @Test
    fun `a lesson says whether it was individual or which group it was with`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Биология"))
        val groupId = env.groupDao.upsert(GroupEntity(name = "11 «А»", subjectId = subj))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(20).minor, groupId = groupId))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        val rows = vm.state.value.classes.sortedBy { it.klass.start }
        assertEquals(LessonKind.INDIVIDUAL, rows[0].kind)
        assertNull("an individual lesson has no group to name", rows[0].groupName)
        assertEquals(LessonKind.GROUP, rows[1].kind)
        assertEquals("11 «А»", rows[1].groupName)
    }

    /** The chips only earn their space when there is a mix to narrow; both counts must be honest. */
    @Test
    fun `the kind tally counts both kinds, and one kind alone offers no narrowing`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Биология"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(mapOf(LessonKind.INDIVIDUAL to 2), vm.state.value.lessonKinds)
        assertFalse("nothing to narrow", vm.state.value.hasBothKinds)

        val groupId = env.groupDao.upsert(GroupEntity(name = "11 «А»", subjectId = subj))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0, groupId = groupId))
        advanceUntilIdle()

        assertEquals(
            mapOf(LessonKind.INDIVIDUAL to 2, LessonKind.GROUP to 1),
            vm.state.value.lessonKinds,
        )
        assertTrue(vm.state.value.hasBothKinds)
    }

    /**
     * The history hangs off the subject it prices, so a student taking two subjects gets two separate
     * timelines rather than one pile the tutor has to disentangle.
     */
    @Test
    fun `each subject carries its own rate history, newest first`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val geometry = env.subjectDao.upsert(SubjectEntity(name = "Геометрия"))
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, geometry, Money.ofMajor(25))
        env.enrollmentRepo.reprice(sid, algebra, Money.ofMajor(35))

        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        val bySubject = vm.state.value.subjects.associateBy { it.subject.name }
        assertEquals(2, bySubject.getValue("Алгебра").history.size)
        assertEquals(Money.ofMajor(35), bySubject.getValue("Алгебра").history.first().to)
        assertEquals(Money.ofMajor(30), bySubject.getValue("Алгебра").history.first().from)
        assertEquals("geometry never moved", 1, bySubject.getValue("Геометрия").history.size)
        assertTrue(bySubject.getValue("Геометрия").history.single().isInitial)
    }

    @Test
    fun `addSubject links and prices the subject together`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.addSubject(math, Money.ofMajor(35))
        advanceUntilIdle()

        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(35), env.enrollmentRepo.priceFor(sid, math)?.amount)
    }

    @Test
    fun `removing a subject with no classes unlinks and unprices it`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.removeSubject(math)
        advanceUntilIdle()

        assertTrue(env.studentDao.getSubjectIds(sid).isEmpty())
        assertNull(env.enrollmentRepo.priceFor(sid, math))
    }

    @Test
    fun `removing a subject the student has classes for is blocked and emits an event`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = math, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))

        val vm = vm(sid)
        val events = mutableListOf<StudentDetailViewModel.Event>()
        observing(vm.state)
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        advanceUntilIdle()

        vm.removeSubject(math)
        advanceUntilIdle()

        assertEquals(listOf(StudentDetailViewModel.Event.REMOVE_BLOCKED_HAS_CLASSES), events)
        // Nothing removed: the priced subject must keep backing its billed class.
        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(30), env.enrollmentRepo.priceFor(sid, math)?.amount)
    }

    @Test
    fun `addPayment ignores a zero amount`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.addPayment(Money.ZERO, today)
        advanceUntilIdle()
        assertTrue(env.paymentDao.items.value.isEmpty())

        vm.addPayment(Money.ofMajor(10), today)
        advanceUntilIdle()
        assertEquals(1, env.paymentDao.items.value.size)
    }

    @Test
    fun `a payment is recorded on the day it was made, not the day it was typed`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            advanceUntilIdle()

            // "She paid me last Tuesday", written up on Sunday. The amount was always recordable;
            // the date was not — every payment used to be stamped with the moment it was entered.
            val lastTuesday = today.minusDays(5)
            vm.addPayment(Money.ofMajor(40), lastTuesday)
            advanceUntilIdle()

            assertEquals(
                "the start of that day: there is no hour the tutor could have supplied",
                lastTuesday.atStartOfDay(zone).toInstant().toEpochMilli(),
                env.paymentDao.items.value.single().dateMillis,
            )
        }

    /**
     * The other half of the rule, and the half that was unreachable while it lived in the screen: a
     * payment dated **today** keeps the current time, so several entered in one sitting stay in the
     * order they were entered. The list is ordered by instant, and a row of identical midnights would
     * shuffle.
     */
    @Test
    fun `payments entered today keep the hour, so a run of them stays in order`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            advanceUntilIdle()

            vm.addPayment(Money.ofMajor(10), today)
            advanceUntilIdle()
            clock.advanceBy(60_000)
            vm.addPayment(Money.ofMajor(20), today)
            advanceUntilIdle()

            val stamps = env.paymentDao.items.value.sortedBy { it.id }.map { it.dateMillis }
            assertEquals("two different instants, not two midnights", 2, stamps.distinct().size)
            assertTrue("the second is later", stamps[1] > stamps[0])
            assertEquals(
                "and both are the moment they were entered, not the start of the day",
                today.atTime(14, 30).atZone(zone).toInstant().toEpochMilli(),
                stamps[0],
            )
        }

    @Test
    fun `editing a payment corrects the row rather than replacing it`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            advanceUntilIdle()

            val recordedOn = today.minusDays(3)
            vm.addPayment(Money.ofMajor(40), recordedOn)
            advanceUntilIdle()
            val original = env.paymentDao.items.value.single()

            // A mistyped amount used to mean deleting the payment and entering it again — two writes,
            // a window where the balance is wrong, and a new id for the same money.
            vm.editPayment(original.toDomain(), Money.ofMajor(45), recordedOn)
            advanceUntilIdle()

            val corrected = env.paymentDao.items.value.single()
            assertEquals("the same row", original.id, corrected.id)
            assertEquals(Money.ofMajor(45).minor, corrected.amountMinor)
            assertEquals(
                "the date was left alone",
                recordedOn.atStartOfDay(zone).toInstant().toEpochMilli(),
                corrected.dateMillis,
            )
        }

    @Test
    fun `editStudent trims fields and ignores a blank name`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent("Аня")
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.editStudent(name = "   ", phone = "x", email = "y", note = "z", mode = ClassMode.REMOTE)
        advanceUntilIdle()
        assertEquals("Аня", env.studentDao.getById(sid)?.name) // blank name rejected

        vm.editStudent(name = "  Борис  ", phone = " +375 ", email = " e@x.co ", note = " n ", mode = ClassMode.REMOTE)
        advanceUntilIdle()
        val updated = env.studentDao.getById(sid)!!
        assertEquals("Борис", updated.name)
        assertEquals("+375", updated.phone)
        assertEquals("e@x.co", updated.email)
        assertEquals("REMOTE", updated.defaultMode)
    }

    @Test
    fun `archiving and restoring a student is reflected in the entity`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()
        assertTrue(env.studentDao.getById(sid)!!.archived)

        vm.setArchived(false)
        advanceUntilIdle()
        assertFalse(env.studentDao.getById(sid)!!.archived)
    }

    /**
     * Whether archiving is worth asking about, and what the question would have to say, are one rule
     * with two halves — and both used to live in `StudentDetailScreen`, twenty lines apart, where no
     * test could reach either.
     */
    @Test
    fun `archiving a student who owes nothing and has no lessons booked asks nothing`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(ArchiveIntent.Archive, vm.state.value.archiveIntent)
        }

    @Test
    fun `the archive question names the balance, the planned lessons, or both`() =
        runTest(mainRule.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val sid = env.fixtures.pricedStudent("Аня", subject, Money.ofMajor(30))
            // One lesson taught and unpaid — a debt — and one still to come.
            env.fixtures.completedLesson(subject, listOf(sid), today.minusDays(2).atTime(10, 0).atZone(zone).toInstant())
            env.fixtures.lesson(subject, listOf(sid), today.plusDays(2).atTime(10, 0).atZone(zone).toInstant())
            val vm = vm(sid)
            observing(vm.state)
            advanceUntilIdle()

            val both = vm.state.value.archiveIntent as ArchiveIntent.Confirm
            assertEquals(Money.ofMajor(30), both.openBalance)
            assertEquals(1, both.upcomingLessons)

            // Paid up, but a lesson still booked: one part, not none.
            vm.addPayment(Money.ofMajor(30), today)
            advanceUntilIdle()
            val lessonsOnly = vm.state.value.archiveIntent as ArchiveIntent.Confirm
            assertNull("nothing outstanding to mention", lessonsOnly.openBalance)
            assertEquals(1, lessonsOnly.upcomingLessons)
        }

    /** Credit counts as much as debt: it leaves the reports the same way, and the tutor owes it back. */
    @Test
    fun `an over-payment is worth asking about too`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.addPayment(Money.ofMajor(25), today)
        advanceUntilIdle()

        val confirm = vm.state.value.archiveIntent as ArchiveIntent.Confirm
        assertEquals(Money.ofMajor(-25), confirm.openBalance)
        assertEquals(0, confirm.upcomingLessons)
    }

    /** Restoring takes nothing away, so it never asks — whatever the student is still carrying. */
    @Test
    fun `restoring an archived student asks nothing even with a debt`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subject, Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(sid), today.minusDays(2).atTime(10, 0).atZone(zone).toInstant())
        env.studentRepo.setArchived(env.studentRepo.getById(sid)!!, archived = true)
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ArchiveIntent.Restore, vm.state.value.archiveIntent)
    }

    // --- Archiving has to settle the schedule too --------------------------------------------------

    /**
     * The hole this closes: archiving removes the student from every report, but their planned lessons
     * stayed live. The maintenance pass would auto-complete one, which bills it, and that income then
     * existed in the database while appearing in no total on any screen — the debt the archive dialog
     * warns about would go on growing where nobody could see it.
     */
    @Test
    fun `archiving calls off the student's planned lessons`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val future = env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )
        // A past lesson still merely planned is the one auto-complete is about to bill.
        val pastPlanned = env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = 1_000,
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )
        val done = env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = 2_000,
                durationMinutes = 60, status = "COMPLETED", priceMinor = 3000, chargeMinor = 3000,
            ),
        )
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        val byId = env.classDao.items.value.associateBy { it.id }
        assertEquals("CANCELLED", byId.getValue(future).status)
        assertEquals("a past lesson nobody confirmed is not about to become income", "CANCELLED", byId.getValue(pastPlanned).status)
        assertEquals("what actually happened is history and stays", "COMPLETED", byId.getValue(done).status)
        assertEquals("its income stays too", 3000, byId.getValue(done).chargeMinor)
        assertTrue(env.studentDao.getById(sid)!!.archived)
    }

    @Test
    fun `archiving says how many lessons it called off`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setArchived(true)
        advanceUntilIdle()

        assertEquals(listOf(StudentDetailViewModel.Event.ARCHIVE_CANCELLED_LESSONS), events)
    }

    @Test
    fun `archiving a student with nothing planned announces nothing`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setArchived(true)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertTrue(env.studentDao.getById(sid)!!.archived)
    }

    /** Restoring is not an undo: a lesson that was called off is a fact the tutor can re-book by hand. */
    @Test
    fun `restoring does not put the called-off lessons back`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = 4_000_000_000_000,
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()
        vm.setArchived(false)
        advanceUntilIdle()

        assertFalse(env.studentDao.getById(sid)!!.archived)
        assertEquals("CANCELLED", env.classDao.items.value.single().status)
    }

    /** The freed hour really is free: a cancelled class gives its slot back. */
    @Test
    fun `the hours of a called-off lesson become bookable again`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val at = 4_000_000_000_000
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subject, startMillis = at,
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        val slot = Scheduling.Slot(Instant.ofEpochMilli(at), 60)
        assertTrue(
            "the cancelled lesson stands in nobody's way",
            env.classRepo.conflictsWith(slot).isEmpty(),
        )
    }

    // --- Writes outlive the screen ---

    /**
     * Every mutator on this card launches into the injected application scope, not `viewModelScope`.
     *
     * This is the card money is entered on, and each write is tapped in a dialog or a snackbar that
     * closes as it is tapped — so «записал платёж, сразу назад» is the ordinary gesture, not an edge
     * case, and in `viewModelScope` it would cancel the write with nothing said.
     *
     * Asserted from the other side, by cancelling the application scope and finding nothing written:
     * `viewModelScope` is live for the whole of this test, so a write launched there would still land
     * and the assertions would pass for the wrong reason. That is what makes this a tripwire rather
     * than a restatement of the code.
     */
    @Test
    fun `writes are launched into the application scope, not the view model's`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent(name = "Аня")
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.priceDao.upsert(PriceEntity(sid, subject, Money.ofMajor(30).minor))
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        mainRule.appScope.cancel()

        vm.addPayment(Money.ofMajor(50), today)
        vm.editStudent("Анна", "", "", "", ClassMode.IN_PERSON)
        vm.setPrice(subject, Money.ofMajor(40))
        advanceUntilIdle()

        assertTrue("no payment was recorded", env.paymentDao.items.value.isEmpty())
        assertEquals("the student was not renamed", "Аня", env.studentDao.getById(sid)!!.name)
        assertEquals(
            "the rate was not changed",
            Money.ofMajor(30).minor,
            env.priceDao.get(sid, subject)!!.amountMinor,
        )
    }

    /**
     * The undo of a deleted payment is the shortest-fused write of the lot: the snackbar offering it is
     * on screen *while* the tutor may be leaving the card, so «Отмена» has to survive that too.
     */
    @Test
    fun `restoring a deleted payment is launched into the application scope`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val payment = Payment(studentId = sid, date = Instant.ofEpochMilli(10), amount = Money.ofMajor(50))
        env.paymentRepo.add(payment)
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()

        val stored = vm.state.value.payments.single()
        vm.deletePayment(stored)
        advanceUntilIdle()
        assertTrue("the delete went through first", env.paymentDao.items.value.isEmpty())

        mainRule.appScope.cancel()
        vm.restorePayment(stored)
        advanceUntilIdle()

        assertTrue("and the undo needed that scope to be alive", env.paymentDao.items.value.isEmpty())
    }

    // --- The write tail: one failure report for every mutator -----------------------------------

    /**
     * Every mutator on the card shares one tail ([StudentDetailViewModel.write]), so a commit that
     * fails comes back as [StudentDetailViewModel.Event.WRITE_FAILED] — a price or payment that
     * silently did not record is the one failure the tutor must not miss, and until the tail was
     * named this path had no test at all. Driven through `failAt`, so the failure is the
     * transaction's, not a rule's refusal.
     */
    @Test
    fun `a write that fails to commit is reported, not swallowed`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.student("Аня")
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        env.transactor.failAt = env.transactor.count + 1

        vm.addSubject(subj, Money.ofMajor(30))
        advanceUntilIdle()

        // Only the report is asserted: the fake DAOs cannot roll back, so what a failed transaction
        // leaves behind is Room's to prove (see `ClassRepositoryAtomicityTest`'s note).
        assertEquals(listOf(StudentDetailViewModel.Event.WRITE_FAILED), events)
    }

    @Test
    fun `a write that succeeds reports nothing`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student("Аня")
        val vm = vm(sid)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.addPayment(Money.ofMajor(50), today)
        advanceUntilIdle()

        assertTrue("nothing to report about a payment that recorded", events.isEmpty())
        assertEquals(1, env.paymentDao.items.value.size)
    }

    /**
     * The dialog's opening day and the stored instant are two halves of one decision, and both must
     * read the same clock — the dialog used to ask the platform (`LocalDate.now()` in a composable),
     * where this pinned clock could never reach.
     */
    @Test
    fun `the payment dialog opens on today by the injected clock`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student("Аня")

        assertEquals(today, vm(sid).paymentDay())
    }
}
