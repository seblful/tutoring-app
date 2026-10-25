package com.seblful.klas.ui.homework

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeworkViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    private val fixtures = env.fixtures

    private val lastMonday: Instant = Instant.parse("2026-05-25T12:00:00Z")

    private fun vm() = mainRule.own(
        HomeworkViewModel(
            env.homeworkRepo, env.homeworkQuery, mainRule.appScope, mainRule.dispatcher, env.clock,
        ),
    )

    @Test
    fun `the tab is Loading until it has been read`() = runTest(mainRule.dispatcher) {
        assertEquals(Loadable.Loading, vm().state.value)
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
    fun `picked homework goes to the archive together, and one undo brings it all back`() =
        runTest(mainRule.dispatcher) {
            val anya = fixtures.student("Аня")
            val boris = fixtures.student("Борис")
            val first = fixtures.homework(Assignee.Student(anya))
            val second = fixtures.homework(Assignee.Student(boris))
            val left = fixtures.homework(Assignee.Student(boris))
            val vm = vm()
            observing(vm.state)

            vm.batch.toggle(first.id)
            vm.batch.toggle(second.id)
            val moved = vm.batch.markDone()
            runCurrent()

            assertEquals(setOf(first.id, second.id), moved.map { it.id }.toSet())
            assertTrue("the pick closes with the action", vm.batch.selected.value.isEmpty())
            assertEquals(listOf(left.id), vm.state.current.lists.current.map { it.homework.id })

            vm.batch.undo(moved)
            runCurrent()
            assertEquals(
                setOf(first.id, second.id, left.id),
                vm.state.current.lists.current.map { it.homework.id }.toSet(),
            )
        }

    @Test
    fun `narrowing the tab lets go of the pick`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val given = fixtures.homework(Assignee.Student(anya))
        val vm = vm()
        observing(vm.state)

        vm.batch.toggle(given.id)
        vm.setFilter(Assignee.Student(anya))

        assertTrue(vm.batch.selected.value.isEmpty())
        assertTrue(vm.batch.markDone().isEmpty())
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

    @Test
    fun `there are no choices to offer before the tab has loaded`() = runTest(mainRule.dispatcher) {
        fixtures.student("Аня")

        assertNull(vm().choices())
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
