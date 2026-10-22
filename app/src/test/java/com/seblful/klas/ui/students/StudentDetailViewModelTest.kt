package com.seblful.klas.ui.students

import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudentDetailViewModelTest : StudentDetailTestFixture() {

    @Test
    fun `addSubject links and prices the subject together`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.fixtures.subject(name = "Математика")
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.addSubject(math, Money.ofMajor(35))
        runCurrent()

        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(35), env.enrollmentRepo.priceFor(sid, math)?.amount)
    }

    @Test
    fun `removing a subject with no classes unlinks and unprices it`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.fixtures.subject(name = "Математика")
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
        env.enrollmentRepo.enroll(sid, math, Money.ofMajor(30))
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.removeSubject(math)
        runCurrent()

        assertTrue(env.studentDao.getSubjectIds(sid).isEmpty())
        assertNull(env.enrollmentRepo.priceFor(sid, math))
    }

    @Test
    fun `removing a subject the student has classes for is blocked and emits an event`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val math = env.fixtures.subject(name = "Математика")
            env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
            env.enrollmentRepo.enroll(sid, math, Money.ofMajor(30))
            env.fixtures.storedClass(
                studentId = sid, subjectId = math, startMillis = 1, durationMinutes = 60,
                status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
            )

            val vm = vm(sid)
            val events = mutableListOf<StudentDetailViewModel.Event>()
            observing(vm.state)
            backgroundScope.launch { vm.events.stream.collect { events += it } }
            runCurrent()

            vm.removeSubject(math)
            runCurrent()

            assertEquals(listOf(StudentDetailViewModel.Event.RemoveBlockedHasClasses), events)
            assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
            assertEquals(Money.ofMajor(30), env.enrollmentRepo.priceFor(sid, math)?.amount)
        }

    @Test
    fun `addPayment ignores a zero amount`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.addPayment(Money.ZERO, today)
        runCurrent()
        assertTrue(env.stored().payments.isEmpty())

        vm.addPayment(Money.ofMajor(10), today)
        runCurrent()
        assertEquals(1, env.stored().payments.size)
    }

    @Test
    fun `a payment is recorded on the day it was made, not the day it was typed`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            runCurrent()

            val lastTuesday = today.minus(5, DateTimeUnit.DAY)
            vm.addPayment(Money.ofMajor(40), lastTuesday)
            runCurrent()

            assertEquals(
                "the start of that day: there is no hour the tutor could have supplied",
                lastTuesday.atStartOfDayIn(zone).toEpochMilliseconds(),
                env.stored().payments.single().dateMillis,
            )
        }

    @Test
    fun `payments entered today keep the hour, so a run of them stays in order`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            runCurrent()

            vm.addPayment(Money.ofMajor(10), today)
            runCurrent()
            clock.advanceBy(60_000)
            vm.addPayment(Money.ofMajor(20), today)
            runCurrent()

            val stamps = env.stored().payments.sortedBy { it.id }.map { it.dateMillis }
            assertEquals("two different instants, not two midnights", 2, stamps.distinct().size)
            assertTrue("the second is later", stamps[1] > stamps[0])
            assertEquals(
                "and both are the moment they were entered, not the start of the day",
                today.atTime(14, 30).toInstant(zone).toEpochMilliseconds(),
                stamps[0],
            )
        }

    @Test
    fun `editing a payment corrects the row rather than replacing it`() =
        runTest(mainRule.dispatcher) {
            val sid = seedStudent()
            val vm = vm(sid)
            observing(vm.state)
            runCurrent()

            val recordedOn = today.minus(3, DateTimeUnit.DAY)
            vm.addPayment(Money.ofMajor(40), recordedOn)
            runCurrent()
            val original = env.stored().payments.single()

            vm.editPayment(original.toDomain(), Money.ofMajor(45), recordedOn)
            runCurrent()

            val corrected = env.stored().payments.single()
            assertEquals("the same row", original.id, corrected.id)
            assertEquals(Money.ofMajor(45).minor, corrected.amountMinor)
            assertEquals(
                "the date was left alone",
                recordedOn.atStartOfDayIn(zone).toEpochMilliseconds(),
                corrected.dateMillis,
            )
        }

    @Test
    fun `editStudent trims fields and ignores a blank name`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent("Аня")
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.editStudent(name = "   ", phone = "x", email = "y", note = "z", mode = ClassMode.REMOTE)
        runCurrent()
        assertEquals("Аня", env.studentDao.getById(sid)?.name) // blank name rejected

        vm.editStudent(
            name = "  Борис  ",
            phone = " +375 29 1234567 ",
            email = " e@x.co ",
            note = " n ",
            mode = ClassMode.REMOTE,
        )
        runCurrent()
        val updated = env.studentDao.getById(sid)!!
        assertEquals("Борис", updated.name)
        assertEquals("+375 29 1234567", updated.phone)
        assertEquals("e@x.co", updated.email)
        assertEquals(ClassMode.REMOTE, updated.defaultMode)
    }

    @Test
    fun `editStudent keeps no phone when only the dialling code was left`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent("Аня")
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        vm.editStudent(name = "Аня", phone = Phone.PREFILL, email = "", note = "", mode = ClassMode.IN_PERSON)
        runCurrent()

        assertEquals("", env.studentDao.getById(sid)!!.phone)
    }

    @Test
    fun `writes are launched into the application scope, not the view model's`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent(name = "Аня")
        val subject = env.fixtures.subject(name = "Математика")
        env.enrollmentRepo.enroll(sid, subject, Money.ofMajor(30))
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        mainRule.appScope.cancel()

        vm.addPayment(Money.ofMajor(50), today)
        vm.editStudent("Анна", "", "", "", ClassMode.IN_PERSON)
        vm.setPrice(subject, Money.ofMajor(40))
        runCurrent()

        assertTrue("no payment was recorded", env.stored().payments.isEmpty())
        assertEquals("the student was not renamed", "Аня", env.studentDao.getById(sid)!!.name)
        assertEquals(
            "the rate was not changed",
            Money.ofMajor(30).minor,
            env.priceDao.get(sid, subject)!!.amountMinor,
        )
    }

    @Test
    fun `restoring a deleted payment is launched into the application scope`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val payment = Payment(studentId = sid, date = Instant.fromEpochMilliseconds(10), amount = Money.ofMajor(50))
        env.paymentRepo.restore(payment)
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()

        val stored = vm.state.current.payments.single()
        vm.deletePayment(stored)
        runCurrent()
        assertTrue("the delete went through first", env.stored().payments.isEmpty())

        mainRule.appScope.cancel()
        vm.restorePayment(stored)
        runCurrent()

        assertTrue("and the undo needed that scope to be alive", env.stored().payments.isEmpty())
    }

    @Test
    fun `a write that fails to commit is reported, not swallowed`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.student("Аня")
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        env.transactor.failAt = env.transactor.count + 1

        vm.addSubject(subj, Money.ofMajor(30))
        runCurrent()

        assertEquals(listOf(StudentDetailViewModel.Event.WriteFailed), events)
        assertEquals("and nothing of it was kept", emptyList<Long>(), env.studentDao.getSubjectIds(sid))
    }

    @Test
    fun `a write that succeeds reports nothing`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student("Аня")
        val vm = vm(sid)
        observing(vm.state)
        runCurrent()
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.addPayment(Money.ofMajor(50), today)
        runCurrent()

        assertTrue("nothing to report about a payment that recorded", events.isEmpty())
        assertEquals(1, env.stored().payments.size)
    }

    @Test
    fun `the overview moves on from a lesson once it ends`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val underWay = env.fixtures.lesson(maths, listOf(anya), clock.now().minus(30.minutes)).single()
        val later = env.fixtures.lesson(maths, listOf(anya), clock.now().plus(2.days)).single()
        val vm = vm(anya)
        observing(vm.overview)
        runCurrent()

        val next = checkNotNull(vm.overview.value?.next)
        assertEquals(underWay, next.row.klass.id)
        assertTrue("begun, so said as under way", next.running)
        val shown = vm.overview.value?.lessons?.map { it.klass.id }
        assertEquals("nothing else begun, so the booking after it", listOf(later), shown)

        val pastItsEnd = 31.minutes
        clock.advanceBy(pastItsEnd.inWholeMilliseconds)
        advanceTimeBy(pastItsEnd.inWholeMilliseconds)

        assertEquals(later, vm.overview.value?.next?.row?.klass?.id)
        assertEquals(listOf(underWay), vm.overview.value?.lessons?.map { it.klass.id })
    }

    @Test
    fun `the overview waits for the card to load`() = runTest(mainRule.dispatcher) {
        val vm = vm(seedStudent())
        assertNull(vm.overview.value)

        observing(vm.overview)
        runCurrent()

        assertEquals("Аня", vm.overview.value?.details?.student?.name)
    }

    @Test
    fun `the payment dialog opens on today by the injected clock`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student("Аня")

        assertEquals(today, vm(sid).paymentDay())
    }

    @Test
    fun `the card lists the student's homework and their group's, current and done`() =
        runTest(mainRule.dispatcher) {
            val maths = env.fixtures.subject()
            val anya = env.fixtures.pricedStudent("Аня", maths)
            val boris = env.fixtures.pricedStudent("Борис", maths)
            val group = env.fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya, boris))
            val tomorrow = clock.now().plus(1.days)
            env.fixtures.lesson(maths, listOf(anya, boris), tomorrow, groupId = group)
            env.fixtures.homework(Assignee.Group(group), text = "группы")
            env.fixtures.homework(Assignee.Student(anya), text = "своё", done = true)
            env.fixtures.homework(Assignee.Student(boris), text = "Бориса")
            val vm = vm(anya)
            observing(vm.state)

            val homework = vm.state.current.homework
            assertEquals(listOf("группы"), homework.current.map { it.text })
            assertEquals("9 «Б»", homework.current.single().title)
            assertEquals(tomorrow, homework.current.single().lessonAt)
            assertEquals(listOf("своё"), homework.archive.map { it.text })
        }

    @Test
    fun `homework set from the card is the student's, for their next lesson`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val tomorrow = clock.now().plus(1.days)
        env.fixtures.lesson(maths, listOf(anya), tomorrow)
        val vm = vm(anya)
        observing(vm.state)

        assertEquals(AssigneeChoice(Assignee.Student(anya), "Аня", tomorrow), vm.homeworkChoice())

        vm.giveHomework("§ 12")
        runCurrent()

        val row = vm.state.current.homework.current.single()
        assertEquals(Assignee.Student(anya), row.homework.assignee)
        assertEquals("§ 12", row.text)
        assertEquals(tomorrow, row.lessonAt)
    }

    @Test
    fun `at midnight the card's homework for tomorrow becomes today's`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        env.fixtures.lesson(maths, listOf(anya), clock.now().plus(1.days))
        env.fixtures.homework(Assignee.Student(anya))
        val vm = vm(anya)
        observing(vm.state)
        runCurrent()
        assertEquals(HomeworkRules.Due.LATER, vm.state.current.homework.current.single().due)

        // 14:30 to a minute past midnight, on both the clock and the scheduler the flow sleeps on.
        val untilMidnight = 9.hours + 31.minutes
        clock.advanceBy(untilMidnight.inWholeMilliseconds)
        advanceTimeBy(untilMidnight.inWholeMilliseconds)

        assertEquals(HomeworkRules.Due.TODAY, vm.state.current.homework.current.single().due)
    }
}
