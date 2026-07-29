package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        rateHistory = env.rateHistoryRepo,
        computation = mainRule.dispatcher,
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

        assertEquals("11 «А»", vm.state.value.group?.name)
        assertEquals("Алгебра", vm.state.value.subjectName)
        assertEquals(Money.ofMajor(25), vm.state.value.group?.price)
    }

    /** A group made before subjects were fixed on the group has none — and the card must not invent one. */
    @Test
    fun `a group with no subject reports none rather than guessing`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Вечер", members = setOf(anya), rate = Money.ofMajor(25))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.value.subjectName)
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
            vm.state.value.members.map { it.student.name },
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

        val attended = vm.state.value.members.associate { it.student.name to it.attended }
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

        assertEquals(1, vm.state.value.members.size)
        assertEquals(0, vm.state.value.members.single().attended)
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

        val lessons = vm.state.value.lessons
        assertEquals("four classes are two lessons", 2, lessons.size)
        assertEquals("newest first", at(2_000), lessons.first().session.start)
        assertEquals(2, lessons.first().attendees)
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

        val totals = vm.state.value.totals
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

        assertEquals(1, vm.state.value.lessons.size)
        assertEquals(Money.ofMajor(20), vm.state.value.totals.earned)
    }

    @Test
    fun `the card carries the group's rate history, newest first`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)
        env.groupRepo.save(
            env.groupRepo.getById(id)!!.copy(price = Money.ofMajor(25)),
            setOf(anya),
        )

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val changes = vm.state.value.rateHistory
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertTrue("the oldest row is where the rate started", changes.last().isInitial)
    }

    @Test
    fun `a group with no lessons reports empty totals`() = runTest(mainRule.dispatcher) {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.value.lessons.isEmpty())
        assertEquals(0, vm.state.value.totals.lessons)
        assertEquals(Money.ZERO, vm.state.value.totals.earned)
    }
}
