package com.seblful.klas.ui.homework

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The «Задания» tab's state: its two lists captioned, the add dialog's choices, the writes, and "today"
 * moving at midnight.
 *
 * **Never `advanceUntilIdle()` here**: [HomeworkViewModel.state] sleeps until the next midnight in a loop,
 * so the scheduler is never idle. Time is advanced by stated amounts instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeworkViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // 15:00 in Minsk on Monday the 1st of June — the environment's clock.
    @get:Rule
    val env = TestEnv()
    private val fixtures = env.fixtures

    private val lastMonday: Instant = Instant.parse("2026-05-25T12:00:00Z")
    private val todayAt17: Instant = Instant.parse("2026-06-01T14:00:00Z")
    private val tomorrow: Instant = Instant.parse("2026-06-02T12:00:00Z")

    private fun vm() = HomeworkViewModel(
        env.homeworkRepo, env.studentRepo, env.groupRepo, mainRule.appScope, mainRule.dispatcher, env.clock,
    )

    @Test
    fun `the tab is Loading until it has been read`() = runTest(mainRule.dispatcher) {
        assertEquals(Loadable.Loading, vm().state.value)
    }

    @Test
    fun `current homework is captioned by whom it is for and says when its lesson is`() =
        runTest(mainRule.dispatcher) {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            fixtures.lesson(maths, listOf(anya), todayAt17)
            fixtures.homework(Assignee.Student(anya), text = "§ 12")
            val vm = vm()
            observing(vm.state)

            val row = vm.state.current.lists.current.single()

            assertEquals("§ 12", row.text)
            assertEquals("Аня", row.title)
            assertEquals(todayAt17, row.lessonAt)
            assertEquals(HomeworkRules.Due.TODAY, row.due)
        }

    @Test
    fun `a group's homework is titled by the group`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Group(group))
        val vm = vm()
        observing(vm.state)

        val row = vm.state.current.lists.current.single()

        assertEquals("9 «Б»", row.title)
        assertTrue(row.isGroup)
        assertEquals(HomeworkRules.Due.UNBOOKED, row.due)
    }

    @Test
    fun `swiping it done moves it to the archive, and back again`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val given = fixtures.homework(Assignee.Student(anya))
        val vm = vm()
        observing(vm.state)

        vm.setDone(given, done = true)
        runCurrent()
        assertTrue(vm.state.current.lists.current.isEmpty())
        assertEquals(listOf(given.id), vm.state.current.lists.archive.map { it.homework.id })

        vm.setDone(given, done = false)
        runCurrent()
        assertEquals(listOf(given.id), vm.state.current.lists.current.map { it.homework.id })
    }

    @Test
    fun `an archived student's homework leaves the tab`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Student(boris), text = "Бориса", done = true)
        env.studentRepo.setArchived(env.studentRepo.getById(boris)!!, archived = true)
        val vm = vm()
        observing(vm.state)

        assertEquals(listOf("Ани"), vm.state.current.lists.current.map { it.text })
        assertTrue(vm.state.current.lists.archive.isEmpty())
    }

    /**
     * The dialog offers whoever is being taught about now first, preselected, and says which lesson the
     * homework will go to — here Борис, whose lesson was an hour ago, over Аня's tomorrow.
     */
    @Test
    fun `the dialog puts the nearest lesson's student first, with the lesson it will go to`() =
        runTest(mainRule.dispatcher) {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val boris = fixtures.pricedStudent("Борис", maths)
            val borisNext = Instant.parse("2026-06-08T12:00:00Z")
            fixtures.lesson(maths, listOf(anya), tomorrow)
            fixtures.lesson(maths, listOf(boris), env.clock.now().minus(3600.seconds))
            fixtures.lesson(maths, listOf(boris), borisNext)
            val archived = fixtures.archivedStudent("Вера")
            val vm = vm()
            observing(vm.state)

            val choices = vm.choices()!!

            assertEquals(listOf("Борис", "Аня"), choices.map { it.name })
            assertEquals(borisNext, choices.first().nextLessonAt)
            assertEquals(tomorrow, choices.last().nextLessonAt)
            assertTrue(choices.none { it.assignee == Assignee.Student(archived) })
        }

    @Test
    fun `with nobody to set it for there is nothing to choose`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)

        assertTrue(vm.choices()!!.isEmpty())
    }

    /**
     * Opened for correcting, the homework's own assignee is always on offer — or the dialog, preselecting
     * from the list, would save it onto somebody else.
     */
    @Test
    fun `correcting homework offers whom it is for even when they are no longer offered`() =
        runTest(mainRule.dispatcher) {
            val anya = fixtures.student("Аня")
            val boris = fixtures.student("Борис")
            fixtures.homework(Assignee.Student(anya))
            val vm = vm()
            observing(vm.state)
            val row = vm.state.current.lists.current.single()
            env.studentRepo.setArchived(env.studentRepo.getById(anya)!!, archived = true)
            runCurrent()

            val choices = vm.choicesFor(row)

            assertEquals(listOf(Assignee.Student(anya), Assignee.Student(boris)), choices.map { it.assignee })
        }

    @Test
    fun `homework given from the tab is stored for whom it was chosen`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val vm = vm()
        observing(vm.state)

        vm.give(Assignee.Student(anya), "§ 12")
        runCurrent()

        val stored = env.homeworkRepo.observeAll().first().single()
        assertEquals(Assignee.Student(anya), stored.assignee)
        assertEquals(env.clock.now(), stored.givenAt)
    }

    /** Left open overnight, the tab calls tomorrow's lesson today's without anything else changing. */
    @Test
    fun `at midnight tomorrow's homework becomes today's`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), tomorrow)
        fixtures.homework(Assignee.Student(anya))
        val vm = vm()
        observing(vm.state)
        assertEquals(HomeworkRules.Due.LATER, vm.state.current.lists.current.single().due)

        // 15:00 to a minute past midnight, on both the clock and the scheduler the flow sleeps on.
        val untilMidnight = 9.hours + 1.minutes
        env.clock.advanceBy(untilMidnight.inWholeMilliseconds)
        advanceTimeBy(untilMidnight.inWholeMilliseconds)

        assertEquals(HomeworkRules.Due.TODAY, vm.state.current.lists.current.single().due)
    }

    /** Before the tab has loaded there is no answer — not "nobody to set it for". */
    @Test
    fun `there are no choices to offer before the tab has loaded`() = runTest(mainRule.dispatcher) {
        fixtures.student("Аня")

        assertNull(vm().choices())
    }

    // --- The filter --------------------------------------------------------------------------------

    @Test
    fun `without homework the filter is empty but everybody can receive a first assignment`() =
        runTest(mainRule.dispatcher) {
            val anya = fixtures.student("Аня")
            val group = fixtures.group(name = "9 «Б»", members = setOf(anya))
            val vm = vm()
            observing(vm.state)

            assertTrue(vm.state.current.filterAssignees.isEmpty())
            assertEquals(
                setOf(Assignee.Student(anya), Assignee.Group(group)),
                vm.choices()!!.map { it.assignee }.toSet(),
            )
        }

    @Test
    fun `the filter offers only people with current or archived homework in name order`() =
        runTest(mainRule.dispatcher) {
            val boris = fixtures.student("Борис")
            val anya = fixtures.student("Аня")
            val withoutHomework = fixtures.student("Вера")
            val archived = fixtures.archivedStudent("Глеб")
            fixtures.homework(Assignee.Student(boris), done = true)
            fixtures.homework(Assignee.Student(anya))
            fixtures.homework(Assignee.Student(archived))
            fixtures.group(name = "Без заданий")
            val vm = vm()
            observing(vm.state)

            val expected = listOf(Assignee.Student(anya), Assignee.Student(boris))
            assertEquals(expected, vm.state.current.filterAssignees)
            assertTrue(vm.choices()!!.any { it.assignee == Assignee.Student(withoutHomework) })

            vm.setFilter(Assignee.Student(anya))
            runCurrent()
            assertEquals("narrowing the list keeps the other filter options", expected, vm.state.current.filterAssignees)
        }

    @Test
    fun `group homework offers the group and its active students in the filter`() =
        runTest(mainRule.dispatcher) {
            val anya = fixtures.student("Аня")
            val boris = fixtures.student("Борис")
            val archived = fixtures.archivedStudent("Глеб")
            fixtures.student("Вера")
            val group = fixtures.group(name = "9 «Б»", members = setOf(anya, boris, archived))
            val withoutHomework = fixtures.group(name = "Без заданий", members = setOf(anya))
            fixtures.homework(Assignee.Group(group), done = true)
            val vm = vm()
            observing(vm.state)

            assertEquals(
                listOf(Assignee.Student(anya), Assignee.Student(boris), Assignee.Group(group)),
                vm.state.current.filterAssignees,
            )

            env.groupRepo.setArchived(env.groupRepo.getById(group)!!, archived = true)
            runCurrent()
            assertTrue(vm.state.current.filterAssignees.isEmpty())
            assertTrue(vm.choices()!!.any { it.assignee == Assignee.Group(withoutHomework) })
        }

    @Test
    fun `the filter follows homework additions edits completion and deletion`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val vm = vm()
        observing(vm.state)

        vm.give(Assignee.Student(anya), "§ 12")
        runCurrent()
        assertEquals(listOf(Assignee.Student(anya)), vm.state.current.filterAssignees)

        val item = vm.state.current.lists.current.single().homework
        vm.setDone(item, done = true)
        runCurrent()
        assertEquals(listOf(Assignee.Student(anya)), vm.state.current.filterAssignees)

        vm.edit(item, Assignee.Student(boris), "§ 13")
        runCurrent()
        assertEquals(listOf(Assignee.Student(boris)), vm.state.current.filterAssignees)

        vm.setFilter(Assignee.Student(boris))
        runCurrent()
        vm.delete(vm.state.current.lists.archive.single().homework)
        runCurrent()
        assertTrue(vm.state.current.filterAssignees.isEmpty())
        assertEquals("Борис", vm.state.current.filterName)

        vm.setFilter(null)
        runCurrent()
        assertNull(vm.state.current.filterName)
    }

    /** Narrowed to a student: theirs and their group's, in both lists — and the chip names them. */
    @Test
    fun `filtered to a student, the tab shows theirs and their group's`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Group(group), text = "группы", done = true)
        fixtures.homework(Assignee.Student(boris), text = "Бориса")
        val vm = vm()
        observing(vm.state)

        vm.setFilter(Assignee.Student(anya))
        runCurrent()

        assertEquals("Аня", vm.state.current.filterName)
        assertEquals(listOf("Ани"), vm.state.current.lists.current.map { it.text })
        assertEquals(listOf("группы"), vm.state.current.lists.archive.map { it.text })
    }

    @Test
    fun `filtered to a group, then cleared, the tab shows the group's and then everybody's`() =
        runTest(mainRule.dispatcher) {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
            fixtures.homework(Assignee.Student(anya), text = "Ани")
            fixtures.homework(Assignee.Group(group), text = "группы")
            val vm = vm()
            observing(vm.state)

            vm.setFilter(Assignee.Group(group))
            runCurrent()
            assertEquals(listOf("группы"), vm.state.current.lists.current.map { it.text })

            vm.setFilter(null)
            runCurrent()
            assertNull(vm.state.current.filterName)
            assertEquals(setOf("Ани", "группы"), vm.state.current.lists.current.map { it.text }.toSet())
        }

    /** Undoing «Вернуть» puts a piece back where it was in the archive — when it was done, kept. */
    @Test
    fun `undoing a swipe puts the piece back as it was`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val done = fixtures.homework(Assignee.Student(anya), at = lastMonday, done = true)
        val vm = vm()
        observing(vm.state)

        vm.setDone(done, done = false)
        runCurrent()
        vm.putBack(done)
        runCurrent()

        assertEquals(lastMonday, vm.state.current.lists.archive.single().homework.doneAt)
    }
}
