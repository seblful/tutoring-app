package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Money
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
            students = env.studentRepo,
            subjects = env.subjectRepo,
            classes = env.classRepo,
            homework = env.homeworkRepo,
            appScope = mainRule.appScope,
            computation = mainRule.dispatcher,
            clock = env.clock,
        ),
    )

    private fun at(key: Long): Instant = Instant.fromEpochMilliseconds(key * 7_200)

    @Test
    fun `the card names the group's subject and rate`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", algebra)
        val id = fixtures.group("11 «А»", subjectId = algebra, members = setOf(anya), rate = Money.ofMajor(25))

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        assertEquals("11 «А»", vm.state.current.group?.name)
        assertEquals("Алгебра", vm.state.current.subjectName)
        assertEquals(Money.ofMajor(25), vm.state.current.group?.price)
    }

    /** A group made before subjects were fixed on the group has none — and the card must not invent one. */
    @Test
    fun `a group with no subject reports none rather than guessing`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Вечер", members = setOf(anya), rate = Money.ofMajor(25))

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        assertNull(vm.state.current.subjectName)
    }

    @Test
    fun `the roster lists members in the app's name order, archived ones last`() = runTest(mainRule.dispatcher) {
        val yaroslav = fixtures.student("Ярослав")
        val anna = fixtures.student("анна")
        val boris = fixtures.archivedStudent("Борис")
        val id = fixtures.group("11 «А»", members = setOf(yaroslav, anna, boris), rate = Money.ofMajor(25))

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        assertEquals(
            listOf("анна", "Ярослав", "Борис"),
            vm.state.current.members.map { it.student.name },
        )
    }

    @Test
    fun `a member's count is of the group's lessons they were in`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), rate = rate, groupId = id)
        fixtures.completedLesson(algebra, listOf(anya), at(2_000), rate = rate, groupId = id)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        val attended = vm.state.current.members.associate { it.student.name to it.attended }
        assertEquals(2, attended["Аня"])
        assertEquals(1, attended["Борис"])
    }

    @Test
    fun `a member who has never attended is still listed, at zero`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        assertEquals(1, vm.state.current.members.size)
        assertEquals(0, vm.state.current.members.single().attended)
    }

    @Test
    fun `lessons are one row per session, newest first, charging every member`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), rate = rate, groupId = id)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(2_000), rate = rate, groupId = id)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        val lessons = vm.state.current.lessons
        assertEquals("four classes are two lessons", 2, lessons.size)
        assertEquals("newest first", at(2_000), lessons.first().session.start)
        assertEquals(Attendance.All(2), lessons.first().attendance)
        assertEquals("both members' charges", Money.ofMajor(40), lessons.first().charge)
    }

    @Test
    fun `the stats count the tutor's lessons and hours but every member's money`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), minutes = 90, rate = rate, groupId = id)
        fixtures.lesson(algebra, listOf(anya, boris), at(2_000), minutes = 60, rate = rate, groupId = id)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        val totals = vm.state.current.totals
        assertEquals(1, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(90, totals.minutes)
        assertEquals(Money.ofMajor(60), totals.earned)
    }

    @Test
    fun `only this group's lessons are counted`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val ours = fixtures.group("11 «А»", members = setOf(anya), rate = rate)
        val theirs = fixtures.group("11 «Б»", members = setOf(anya), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya), at(1_000), rate = rate, groupId = ours)
        fixtures.completedLesson(algebra, listOf(anya), at(2_000), rate = rate, groupId = theirs)
        // An individual class of the same student is not the group's either.
        fixtures.completedLesson(algebra, listOf(anya), at(3_000), rate = Money.ofMajor(50))

        val vm = vm(ours)
        observing(vm.state)
        runCurrent()

        assertEquals(1, vm.state.current.lessons.size)
        assertEquals(Money.ofMajor(20), vm.state.current.totals.earned)
    }

    @Test
    fun `a group with no lessons reports empty totals`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        runCurrent()

        assertTrue(vm.state.current.lessons.isEmpty())
        assertEquals(0, vm.state.current.totals.lessons)
        assertEquals(Money.ZERO, vm.state.current.totals.earned)
    }

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
    fun `archiving a group with planned lessons calls them off and says how many`() = runTest(mainRule.dispatcher) {
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

        assertEquals(listOf(GroupDetailViewModel.Event.ArchiveCancelledLessons(2)), events)
        assertEquals(0, vm.state.current.plannedLessons)
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
    fun `the card lists the group's homework, current and done`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Group(group), text = "задачи")
        fixtures.homework(Assignee.Group(group), text = "прошлое", done = true)
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        val vm = vm(group)
        observing(vm.state)

        assertEquals(listOf("задачи"), vm.state.current.homework.current.map { it.text })
        assertEquals(listOf("прошлое"), vm.state.current.homework.archive.map { it.text })
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

        assertEquals(AssigneeChoice(Assignee.Group(group), "9 «Б»", tomorrow), vm.homeworkChoice())

        vm.giveHomework("§ 12")
        runCurrent()

        val row = vm.state.current.homework.current.single()
        assertEquals(Assignee.Group(group), row.homework.assignee)
        assertEquals(tomorrow, row.lessonAt)
    }

    @Test
    fun `at midnight the card's homework for tomorrow becomes today's`() = runTest(mainRule.dispatcher) {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.lesson(maths, listOf(anya), env.clock.now().plus((86_400).seconds), groupId = group)
        fixtures.homework(Assignee.Group(group))
        val vm = vm(group)
        observing(vm.state)
        runCurrent()
        assertEquals(HomeworkRules.Due.LATER, vm.state.current.homework.current.single().due)

        val untilMidnight = 9.hours + 1.minutes
        env.clock.advanceBy(untilMidnight.inWholeMilliseconds)
        advanceTimeBy(untilMidnight.inWholeMilliseconds)

        assertEquals(HomeworkRules.Due.TODAY, vm.state.current.homework.current.single().due)
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
