package com.seblful.klas.ui.today

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.notifications.RecentMarks
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The test clock reads 15:00 in Minsk on 1 June 2026. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    private val fixtures get() = env.fixtures

    private val marks = RecentMarks()

    private fun vm() = mainRule.own(
        TodayViewModel(
            classes = env.classRepo,
            students = env.studentRepo,
            subjects = env.subjectRepo,
            groups = env.groupRepo,
            balances = env.balanceRepo,
            homework = env.homeworkRepo,
            settings = env.settingsRepo,
            payments = env.paymentRepo,
            recentMarks = marks,
            clock = env.clock,
            appScope = mainRule.appScope,
            computation = mainRule.dispatcher,
        ),
    )

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = TODAY): Instant =
        day.atTime(hour, minute).toInstant(env.clock.zone)

    private class Day(val anya: Long, val boris: Long, val vera: Long)

    /** Аня's lesson is over and unmarked, Борис's is on now, Вера's is this evening; Аня also had one yesterday. */
    private suspend fun day(): Day {
        val subject = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", subject)
        val boris = fixtures.pricedStudent("Борис", subject)
        val vera = fixtures.pricedStudent("Вера", subject)
        fixtures.lesson(subject, listOf(anya), at(10, day = TODAY.minusDay()))
        fixtures.lesson(subject, listOf(anya), at(10))
        fixtures.lesson(subject, listOf(boris), at(14, 30))
        fixtures.lesson(subject, listOf(vera), at(18))
        return Day(anya, boris, vera)
    }

    @Test
    fun `today lists only today's lessons, in order, each where the clock stands on it`() =
        runTest(mainRule.dispatcher) {
            day()
            val vm = vm()
            observing(vm.state)
            runCurrent()

            val lessons = vm.state.current.lessons
            assertEquals(listOf("Аня", "Борис", "Вера"), lessons.map { it.who })
            assertEquals(
                listOf(LessonPhase.OVERDUE, LessonPhase.IN_PROGRESS, LessonPhase.UPCOMING),
                lessons.map { it.phase },
            )
            assertEquals("the lesson on now leads, not the one after it", "Борис", vm.state.current.next?.who)
            assertEquals(listOf("Аня"), vm.state.current.toMark.map { it.who })
        }

    @Test
    fun `a debtor is listed with what they owe and when they last paid`() = runTest(mainRule.dispatcher) {
        val subject = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", subject)
        fixtures.pricedStudent("Борис", subject)
        fixtures.setStatus(fixtures.lesson(subject, listOf(anya), at(10)).single(), LessonStatus.COMPLETED)
        env.paymentRepo.record(anya, Money.ofMajor(10), TODAY.minusDay())

        val vm = vm()
        observing(vm.state)
        runCurrent()

        val debtor = vm.state.current.debtors.single()
        assertEquals("Аня", debtor.name)
        assertEquals(Money.ofMajor(20), debtor.owed)
        assertEquals(TODAY.minusDay(), debtor.lastPaidAt?.toLocalDateTime(env.clock.zone)?.date)
    }

    @Test
    fun `a mark made outside the app is offered for ten minutes, and its undo re-plans the lesson`() =
        runTest(mainRule.dispatcher) {
            day()
            val anya = env.classRepo.observeBetween(at(9), at(11)).first().single().id
            val boris = env.classRepo.observeBetween(at(14), at(15)).first().single().id
            fun taught(classId: Long) = runBlocking {
                val written = env.classRepo.setStatus(env.classRepo.getSessionFor(classId)!!, LessonStatus.COMPLETED)
                env.classRepo.getSessionFor(classId)!!.also { check(written is LessonWrite.Written) }
            }
            marks.record(taught(anya), env.clock.now())
            marks.record(taught(boris), env.clock.now() - 11.minutes)

            val vm = vm()
            observing(vm.state)
            runCurrent()

            val recent = vm.state.current.recentMarks
            assertEquals("Борис's was marked too long ago to undo here", listOf("Аня"), recent.map { it.who })

            vm.undo(recent.single())
            runCurrent()

            assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(anya)?.status)
            assertTrue(vm.state.current.recentMarks.isEmpty())
        }

    @Test
    fun `a cancelled lesson is not on the day`() = runTest(mainRule.dispatcher) {
        day()
        val evening = env.classRepo.getSessionFor(
            env.classRepo.observeBetween(at(17), at(19)).first().single().id,
        )!!
        env.classRepo.setStatus(evening, LessonStatus.CANCELLED)
        val vm = vm()
        observing(vm.state)
        runCurrent()

        assertEquals(listOf("Аня", "Борис"), vm.state.current.lessons.map { it.who })
    }

    @Test
    fun `mark all taught settles every finished lesson, and one undo takes them all back`() =
        runTest(mainRule.dispatcher) {
            val subject = fixtures.subject("Алгебра")
            val anya = fixtures.pricedStudent("Аня", subject)
            val boris = fixtures.pricedStudent("Борис", subject)
            fixtures.lesson(subject, listOf(anya), at(9))
            fixtures.lesson(subject, listOf(boris), at(11))
            fixtures.lesson(subject, listOf(boris), at(18))
            val vm = vm()
            observing(vm.state)
            runCurrent()

            vm.markAllTaught()
            runCurrent()
            val marked = vm.events.stream.first() as TodayViewModel.Event.Marked

            assertEquals(2, marked.sessions.size)
            assertTrue(vm.state.current.toMark.isEmpty())
            assertEquals(
                "the evening lesson has not happened, so it is not marked",
                LessonPhase.UPCOMING,
                vm.state.current.lessons.last().phase,
            )
            assertEquals(2, vm.state.current.outstanding.students)

            vm.undo(marked)
            runCurrent()

            assertEquals(listOf("Аня", "Борис"), vm.state.current.toMark.map { it.who })
            assertEquals(0, vm.state.current.outstanding.students)
        }

    @Test
    fun `an undo leaves a lesson that changed since it was marked`() = runTest(mainRule.dispatcher) {
        day()
        val vm = vm()
        observing(vm.state)
        runCurrent()
        val row = vm.state.current.toMark.single()

        vm.markTaught(row)
        runCurrent()
        val marked = vm.events.stream.first() as TodayViewModel.Event.Marked
        env.classRepo.setStatus(env.classRepo.getSessionFor(row.session.first.id)!!, LessonStatus.CANCELLED)
        vm.undo(marked)
        runCurrent()

        assertEquals(TodayViewModel.Event.UndoChanged, vm.events.stream.first())
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getSessionFor(row.session.first.id)!!.status)
    }

    @Test
    fun `homework due at a lesson today is listed, homework with no lesson left today is not`() =
        runTest(mainRule.dispatcher) {
            val people = day()
            fixtures.homework(Assignee.Student(people.vera), text = "§ 3", at = at(9))
            fixtures.homework(Assignee.Student(people.boris), text = "§ 9", at = at(15, 30))
            val vm = vm()
            observing(vm.state)
            runCurrent()

            assertEquals(listOf("§ 3"), vm.state.current.homework.map { it.homework.text })
        }

    @Test
    fun `an old backup is said only once there is someone to lose`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        runCurrent()
        assertFalse("a fresh install has nothing to back up", vm.state.current.backupStale)

        day()
        runCurrent()
        assertTrue("never backed up, with students", vm.state.current.backupStale)

        env.settingsRepo.update { it.copy(lastExportAt = env.clock.now() - 2.days) }
        runCurrent()
        assertFalse(vm.state.current.backupStale)
    }

    private fun LocalDate.minusDay(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - 1)

    private companion object {
        val TODAY = LocalDate(2026, 6, 1)
    }
}
