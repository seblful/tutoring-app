package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Money
import com.seblful.klas.queries.groups.GroupDetailsQuery
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupDetailViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    private val fixtures get() = env.fixtures

    private val rate = Money.ofMajor(20)

    private fun vm(id: Long) = mainRule.own(
        GroupDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to id)),
            groups = env.groupRepo,
            details = GroupDetailsQuery(
                env.groupRepo, env.studentRepo, env.subjectRepo, env.classRepo,
                env.homeworkQuery, env.balanceRepo,
            ),
            homework = env.homeworkRepo,
            lessonChanges = env.lessonChanges,
            appScope = mainRule.appScope,
            computation = mainRule.dispatcher,
            clock = env.clock,
        ),
    )

    private fun at(key: Long): Instant = Instant.fromEpochMilliseconds(key * 7_200)

    @Test
    fun `archiving a group keeps it and its roster`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Выпускники", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        vm.setArchived(true)
        runCurrent()

        assertTrue(vm.state.current.group?.archived == true)
        assertTrue(env.groupRepo.getById(id)!!.archived)
        assertEquals(listOf(anya), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `archiving a group calls off its planned lessons, says how many, and undoes`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.group("Выпускники", subjectId = maths, members = setOf(anya), rate = rate)
        val tomorrow = env.clock.now() + 24.hours
        fixtures.lesson(maths, listOf(anya), tomorrow, groupId = id, rate = rate)
        fixtures.lesson(maths, listOf(anya), tomorrow + 48.hours, groupId = id, rate = rate)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()
        assertEquals("the card knows what archiving would cost", 2, vm.state.current.plannedLessons)

        val events = mutableListOf<GroupDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        vm.setArchived(true)
        runCurrent()

        val archived = events.single() as GroupDetailViewModel.Event.Archived
        assertEquals(2, archived.archive.cancelledLessons)
        assertEquals(0, vm.state.current.plannedLessons)

        vm.undoArchive(archived)
        runCurrent()

        assertEquals(2, vm.state.current.plannedLessons)
        assertFalse(vm.state.current.group!!.archived)
    }

    @Test
    fun `an unused group can be deleted`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Ошибка", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        var deleted = false
        vm.delete { deleted = true }
        runCurrent()

        assertTrue(deleted)
        assertNull(env.groupRepo.getById(id))
    }

    @Test
    fun `deleting is refused while the group has lessons`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", algebra)
        val id = fixtures.group("11 «А»", subjectId = algebra, members = setOf(anya), rate = rate)
        fixtures.completedLesson(
            subjectId = algebra,
            students = listOf(anya),
            start = at(1),
            rate = rate,
            groupId = id,
        )

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        var deleted = false
        val events = mutableListOf<GroupDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        vm.delete { deleted = true }
        runCurrent()

        assertFalse(deleted)
        assertEquals(listOf(GroupDetailViewModel.Event.DeleteBlockedHasLessons), events)
        assertNotNull("the group survives so its lessons still name it", env.groupRepo.getById(id))
    }

    @Test
    fun `homework set from the card is the group's, for its next lesson`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        val tomorrow = env.clock.now().plus((86_400).seconds)
        fixtures.lesson(maths, listOf(anya), tomorrow, groupId = group)
        val vm = vm(group)
        observing(vm.state)

        assertEquals(AssigneeChoice(Assignee.Group(group), "9 «Б»", tomorrow), vm.cardHomework.choice())

        vm.cardHomework.give("§ 12")
        runCurrent()

        val row = vm.state.current.homework.current.single()
        assertEquals(Assignee.Group(group), row.homework.assignee)
        assertEquals(tomorrow, row.lessonAt)
    }

    @Test
    fun `the overview leads with the lesson under way, then moves on once it ends`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", subjectId = algebra, members = setOf(anya, boris), rate = rate)
        val now = env.clock.now()
        val members = listOf(anya, boris)
        val underWay = fixtures.lesson(algebra, members, now.minus(30.minutes), rate = rate, groupId = id).min()
        val later = fixtures.lesson(algebra, members, now.plus(48.hours), rate = rate, groupId = id).min()

        val vm = vm(id)
        observing(vm.overview)
        runCurrent()

        val next = checkNotNull(vm.overview.value?.next)
        assertEquals("one class of the lesson opens it", underWay, next.classId)
        assertTrue("begun, so said as under way", next.running)
        assertEquals("Алгебра", next.subjectName)
        assertNull("the group's own card does not name the group again", next.groupName)
        assertEquals(listOf(later), vm.overview.value?.lessons?.map { it.session.first.id })

        val pastItsEnd = 31.minutes
        env.clock.advanceBy(pastItsEnd.inWholeMilliseconds)
        advanceTimeBy(pastItsEnd.inWholeMilliseconds)

        assertEquals(later, vm.overview.value?.next?.classId)
        assertEquals(listOf(underWay), vm.overview.value?.lessons?.map { it.session.first.id })
    }

    @Test
    fun `the overview waits for the card to load`() = runTest(mainRule.dispatcher) {
        val id = fixtures.group("Вечер", members = setOf(fixtures.student("Аня")), rate = rate)
        val vm = vm(id)
        assertNull(vm.overview.value)

        observing(vm.overview)
        runCurrent()

        assertEquals("Вечер", vm.overview.value?.details?.group?.name)
    }
}
