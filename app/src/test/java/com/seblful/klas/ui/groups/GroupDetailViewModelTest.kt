package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.domain.Attendance
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.homework.AssigneeChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val fixtures get() = env.fixtures

    /** The group's rate throughout, so a lesson's charge is this scaled by its duration. */
    private val rate = Money.ofMajor(20)

    private fun vm(id: Long) = GroupDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        groups = env.groupRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
        classes = env.classRepo,
        homework = env.homeworkRepo,
        appScope = mainRule.appScope,
        computation = mainRule.dispatcher,
        clock = env.clock,
    )

    /**
     * Key *n* gets its own two-hour block. These tests are about counting, not scheduling, but they
     * seed through the repository — which holds one lesson at a time, so back-to-back keys must not
     * contest an hour.
     */
    private fun at(key: Long): Instant = Instant.ofEpochMilli(key * 7_200)

    @Test
    fun `the card names the group's subject and rate`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", algebra)
        val id = fixtures.group("11 «А»", subjectId = algebra, members = setOf(anya), rate = Money.ofMajor(25))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(
            listOf("анна", "Ярослав", "Борис"),
            vm.state.current.members.map { it.student.name },
        )
    }

    /**
     * The roster says who is *in* the group; this count says who is actually turning up. Someone left
     * out of lesson after lesson is still a member, and the card should show that rather than hide it.
     */
    @Test
    fun `a member's count is of the group's lessons they were in`() = runTest(mainRule.dispatcher) {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        // Two lessons; Boris was only in the first.
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), rate = rate, groupId = id)
        fixtures.completedLesson(algebra, listOf(anya), at(2_000), rate = rate, groupId = id)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        val lessons = vm.state.current.lessons
        assertEquals("four classes are two lessons", 2, lessons.size)
        assertEquals("newest first", at(2_000), lessons.first().session.start)
        // The row publishes the case rather than the two counts: with nobody marked absent, a lesson of
        // two reads as its whole roster and never as «2 из 2». See [Attendance].
        assertEquals(Attendance.All(2), lessons.first().attendance)
        assertEquals("both members' charges", Money.ofMajor(40), lessons.first().charge)
    }

    /**
     * The stat row's numbers are the tutor's, not the members': one lesson of an hour and a half is
     * ninety minutes however many people were in the room, while the money is every member's charge.
     *
     * The money used to be asserted as 40 — two members at the group's flat hourly rate — because the
     * test wrote the charge into the row itself and ignored the duration it had asked for. Booked and
     * completed the way the app does it, ninety minutes at 20 an hour bills each member 30.
     */
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
        advanceUntilIdle()

        val totals = vm.state.current.totals
        assertEquals(1, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(90, totals.minutes)
        assertEquals(Money.ofMajor(60), totals.earned)
    }

    /** Another group's lessons must not appear here, however similar their times. */
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
        advanceUntilIdle()

        assertEquals(1, vm.state.current.lessons.size)
        assertEquals(Money.ofMajor(20), vm.state.current.totals.earned)
    }

    @Test
    fun `a group with no lessons reports empty totals`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.current.lessons.isEmpty())
        assertEquals(0, vm.state.current.totals.lessons)
        assertEquals(Money.ZERO, vm.state.current.totals.earned)
    }

    /**
     * **Retiring a group is the card's, not the form's.**
     *
     * Archiving and deleting used to sit in a «danger zone» at the foot of the group's *edit form*,
     * which put the same lifecycle verb on the card for a student and on the form for a group — and
     * meant reaching it involved scrolling past the whole roster. These three moved here with it.
     *
     * The roster is untouched by archiving: a retired group is still a record of who was in it.
     */
    @Test
    fun `archiving a group keeps it and its roster`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Выпускники", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        assertTrue(vm.state.current.group?.archived == true)
        assertTrue(env.groupRepo.getById(id)!!.archived)
        assertEquals(listOf(anya), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `an unused group can be deleted`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Ошибка", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        assertNull(env.groupRepo.getById(id))
    }

    /**
     * A group with lessons is refused, because its classes must keep naming it — and the refusal is
     * *said*, through the same one-shot event channel every other refusal in the app uses. The card
     * offers archiving instead.
     */
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
        advanceUntilIdle()

        var deleted = false
        val events = mutableListOf<GroupDetailViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertFalse(deleted)
        assertEquals(listOf(GroupDetailViewModel.Event.DELETE_BLOCKED_HAS_LESSONS), events)
        assertNotNull("the group survives so its lessons still name it", env.groupRepo.getById(id))
    }

    // --- Homework -----------------------------------------------------------------------------------

    /** The group's own homework, current and done — not a member's. */
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
        val tomorrow = env.clock.instant().plusSeconds(86_400)
        fixtures.lesson(maths, listOf(anya), tomorrow, groupId = group)
        val vm = vm(group)
        observing(vm.state)

        assertEquals(AssigneeChoice(Assignee.Group(group), "9 «Б»", tomorrow), vm.homeworkChoice())

        vm.giveHomework("§ 12")
        advanceUntilIdle()

        val row = vm.state.current.homework.current.single()
        assertEquals(Assignee.Group(group), row.homework.assignee)
        assertEquals(tomorrow, row.lessonAt)
    }
}
