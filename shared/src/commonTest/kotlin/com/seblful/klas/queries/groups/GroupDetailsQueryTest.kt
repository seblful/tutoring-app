package com.seblful.klas.queries.groups

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailsQueryTest {
    private val env = TestEnv()
    private val store = env.store()
    private val fixtures get() = env.fixtures
    private val rate = Money.ofMajor(20)

    @AfterTest
    fun close() = env.close()

    private fun CoroutineScope.watch(id: Long): StateFlow<GroupDetails?> =
        store.groupDetails.observe(id).stateIn(this, SharingStarted.Eagerly, null)

    private fun at(key: Long): Instant = Instant.fromEpochMilliseconds(key * 7_200)

    @Test
    fun `the card names the group's subject and rate`() = runTest {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.pricedStudent("Аня", algebra)
        val id = fixtures.group("11 «А»", subjectId = algebra, members = setOf(anya), rate = Money.ofMajor(25))

        val details = backgroundScope.watch(id)
        runCurrent()

        assertEquals("11 «А»", details.value!!.group?.name)
        assertEquals("Алгебра", details.value!!.subjectName)
        assertEquals(Money.ofMajor(25), details.value!!.group?.price)
    }

    @Test
    fun `a group with no subject reports none rather than guessing`() = runTest {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("Вечер", members = setOf(anya), rate = Money.ofMajor(25))

        val details = backgroundScope.watch(id)
        runCurrent()

        assertNull(details.value!!.subjectName)
    }

    @Test
    fun `the roster lists members in the app's name order archived ones last`() = runTest {
        val yaroslav = fixtures.student("Ярослав")
        val anna = fixtures.student("анна")
        val boris = fixtures.archivedStudent("Борис")
        val id = fixtures.group("11 «А»", members = setOf(yaroslav, anna, boris), rate = Money.ofMajor(25))

        val details = backgroundScope.watch(id)
        runCurrent()

        assertEquals(
            listOf("анна", "Ярослав", "Борис"),
            details.value!!.members.map { it.student.name },
        )
    }

    @Test
    fun `a member's count is of the group's taught lessons they were in`() = runTest {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), rate = rate, groupId = id)
        fixtures.completedLesson(algebra, listOf(anya), at(2_000), rate = rate, groupId = id)

        val details = backgroundScope.watch(id)
        runCurrent()

        val members = details.value!!.members.associateBy { it.student.name }
        assertEquals(2, members.getValue("Аня").present)
        assertEquals(2, members.getValue("Аня").held)
        assertEquals(1, members.getValue("Борис").present)
        assertEquals(1, members.getValue("Борис").held)
        // Two taught lessons at the group's rate, nothing paid: each member owes for the ones they were in.
        assertEquals(Balance.Owed(rate + rate), members.getValue("Аня").balance)
    }

    @Test
    fun `a member who has never attended is still listed at zero`() = runTest {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val details = backgroundScope.watch(id)
        runCurrent()

        assertEquals(1, details.value!!.members.size)
        assertEquals(0, details.value!!.members.single().held)
    }

    @Test
    fun `lessons are one row per session newest first charging every member`() = runTest {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), rate = rate, groupId = id)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(2_000), rate = rate, groupId = id)

        val details = backgroundScope.watch(id)
        runCurrent()

        val lessons = details.value!!.lessons
        assertEquals(2, lessons.size, "four classes are two lessons")
        assertEquals(at(2_000), lessons.first().session.start, "newest first")
        assertEquals(Attendance.All(2), lessons.first().attendance)
        assertEquals(Money.ofMajor(40), lessons.first().charge, "both members' charges")
    }

    @Test
    fun `the stats count the tutor's lessons and hours but every member's money`() = runTest {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val id = fixtures.group("11 «А»", members = setOf(anya, boris), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya, boris), at(1_000), minutes = 90, rate = rate, groupId = id)
        fixtures.lesson(algebra, listOf(anya, boris), at(2_000), minutes = 60, rate = rate, groupId = id)

        val details = backgroundScope.watch(id)
        runCurrent()

        val totals = details.value!!.totals
        assertEquals(1, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(90, totals.minutes)
        assertEquals(Money.ofMajor(60), totals.earned)
    }

    @Test
    fun `only this group's lessons are counted`() = runTest {
        val algebra = fixtures.subject("Алгебра")
        val anya = fixtures.student("Аня")
        val ours = fixtures.group("11 «А»", members = setOf(anya), rate = rate)
        val theirs = fixtures.group("11 «Б»", members = setOf(anya), rate = rate)
        fixtures.completedLesson(algebra, listOf(anya), at(1_000), rate = rate, groupId = ours)
        fixtures.completedLesson(algebra, listOf(anya), at(2_000), rate = rate, groupId = theirs)
        // An individual class of the same student is not the group's either.
        fixtures.completedLesson(algebra, listOf(anya), at(3_000), rate = Money.ofMajor(50))

        val details = backgroundScope.watch(ours)
        runCurrent()

        assertEquals(1, details.value!!.lessons.size)
        assertEquals(Money.ofMajor(20), details.value!!.totals.earned)
    }

    @Test
    fun `a group with no lessons reports empty totals`() = runTest {
        val anya = fixtures.student("Аня")
        val id = fixtures.group("11 «А»", members = setOf(anya), rate = rate)

        val details = backgroundScope.watch(id)
        runCurrent()

        assertTrue(details.value!!.lessons.isEmpty())
        assertEquals(0, details.value!!.totals.lessons)
        assertEquals(Money.ZERO, details.value!!.totals.earned)
    }

    @Test
    fun `the card lists the group's homework current and done`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Group(group), text = "задачи")
        fixtures.homework(Assignee.Group(group), text = "прошлое", done = true)
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        val details = backgroundScope.watch(group)
        runCurrent()

        assertEquals(listOf("задачи"), details.value!!.homework.current.map { it.text })
        assertEquals(listOf("прошлое"), details.value!!.homework.archive.map { it.text })
    }

    @Test
    fun `at midnight the card's homework for tomorrow becomes today's`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.lesson(maths, listOf(anya), env.clock.now().plus((86_400).seconds), groupId = group)
        fixtures.homework(Assignee.Group(group))
        val details = backgroundScope.watch(group)
        runCurrent()
        assertEquals(HomeworkRules.Due.LATER, details.value!!.homework.current.single().due)

        val untilMidnight = 9.hours + 1.minutes
        env.clock.advanceBy(untilMidnight.inWholeMilliseconds)
        advanceTimeBy(untilMidnight.inWholeMilliseconds)

        assertEquals(HomeworkRules.Due.TODAY, details.value!!.homework.current.single().due)
    }

    @Test
    fun `a missing group stays distinct from an empty group`() = runTest {
        val missing = backgroundScope.watch(999)
        val id = fixtures.group()
        val existing = backgroundScope.watch(id)
        runCurrent()

        assertNull(missing.value!!.group)
        assertEquals(id, existing.value!!.group?.id)
        assertTrue(existing.value!!.lessons.isEmpty())
    }
}
