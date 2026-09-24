package com.seblful.klas.ui.homework

import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The «Задания» tab's state: the worklist captioned, marking a verdict, and "today" moving at midnight.
 *
 * **Never `advanceUntilIdle()` here**: [HomeworkViewModel.lists] sleeps until the next midnight in a loop,
 * so the scheduler is never idle. Time is advanced by stated amounts instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeworkViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // 15:00 in Minsk on Monday the 1st of June — the environment's clock.
    private val env = TestEnv()
    private val fixtures = env.fixtures

    private val lastMonday: Instant = Instant.parse("2026-05-25T12:00:00Z")
    private val todayAt17: Instant = Instant.parse("2026-06-01T14:00:00Z")
    private val tomorrow: Instant = Instant.parse("2026-06-02T12:00:00Z")

    private fun vm() = HomeworkViewModel(
        env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
        mainRule.dispatcher, env.clock,
    )

    private suspend fun setHomework(classId: Long, text: String) =
        env.classRepo.setHomework(env.classRepo.getSessionFor(classId)!!, text)

    @Test
    fun `the lists are Loading until they have been read`() = runTest(mainRule.dispatcher) {
        assertEquals(Loadable.Loading, vm().lists.value)
    }

    @Test
    fun `homework due today is to check, captioned by student and subject`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), lastMonday).single()
        fixtures.lesson(maths, listOf(anya), todayAt17)
        setHomework(set, "§12")
        val vm = vm()
        observing(vm.lists)

        val row = vm.lists.current.today.single()

        assertEquals("§12", row.text)
        assertEquals("Аня", row.title)
        assertEquals("Алгебра", row.subjectName)
        assertEquals(1, vm.lists.current.toCheck)
    }

    /** A group's homework names the group, and each member beside their own verdict. */
    @Test
    fun `a group's homework lists every member by name`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya, boris))
        val set = fixtures.lesson(maths, listOf(anya, boris), lastMonday, groupId = group).first()
        fixtures.lesson(maths, listOf(anya, boris), tomorrow, groupId = group)
        setHomework(set, "Задачи")
        val vm = vm()
        observing(vm.lists)

        val row = vm.lists.current.waiting.single()

        assertEquals("9 «Б»", row.title)
        assertEquals(listOf("Аня", "Борис"), row.members.map { it.name })
        assertTrue(row.isGroup)
    }

    @Test
    fun `marking everybody takes the homework off the list`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), lastMonday).single()
        fixtures.lesson(maths, listOf(anya), todayAt17)
        setHomework(set, "§12")
        val vm = vm()
        observing(vm.lists)

        vm.setResult(vm.lists.current.today.single().members.single().klass, HomeworkResult.DONE)
        runCurrent()

        assertEquals(0, vm.lists.current.toCheck)
        assertEquals(HomeworkResult.DONE, env.classRepo.getById(set)!!.homeworkResult)
    }

    /** Left open overnight, the tab moves tomorrow's homework to today's without anything else changing. */
    @Test
    fun `at midnight tomorrow's homework becomes today's`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), lastMonday).single()
        fixtures.lesson(maths, listOf(anya), tomorrow)
        setHomework(set, "§12")
        val vm = vm()
        observing(vm.lists)
        assertEquals(1, vm.lists.current.waiting.size)

        // 15:00 to a minute past midnight, on both the clock and the scheduler the flow sleeps on.
        val untilMidnight = Duration.ofHours(9).plusMinutes(1)
        env.clock.advanceBy(untilMidnight.toMillis())
        advanceTimeBy(untilMidnight.toMillis())

        assertEquals(1, vm.lists.current.today.size)
        assertTrue(vm.lists.current.waiting.isEmpty())
    }
}
