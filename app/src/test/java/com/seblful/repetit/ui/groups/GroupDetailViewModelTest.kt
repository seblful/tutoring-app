package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm(id: Long) = GroupDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        groups = env.groupRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
        classes = env.classRepo,
        rateHistory = env.rateHistoryRepo,
    )

    private suspend fun student(name: String) = env.studentDao.upsert(StudentEntity(name = name))

    private suspend fun groupClass(
        groupId: Long,
        studentId: Long,
        subjectId: Long,
        startMillis: Long,
        minutes: Int = 60,
        status: String = "COMPLETED",
        charge: Money = Money.ofMajor(20),
    ) = env.classDao.upsert(
        ClassEntity(
            studentId = studentId,
            subjectId = subjectId,
            startMillis = startMillis,
            durationMinutes = minutes,
            priceMinor = Money.ofMajor(20).minor,
            status = status,
            chargeMinor = if (status == "COMPLETED") charge.minor else 0,
            groupId = groupId,
        ),
    )

    @Test
    fun `the card names the group's subject and rate`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.studentDao.addSubjectLink(com.seblful.repetit.data.local.StudentSubjectCrossRef(anya, algebra))
        val id = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(25), subjectId = algebra),
            setOf(anya),
        )

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals("11 «А»", vm.state.value.group?.name)
        assertEquals("Алгебра", vm.state.value.subjectName)
        assertEquals(Money.ofMajor(25), vm.state.value.group?.price)
    }

    /** A group made before subjects were fixed on the group has none — and the card must not invent one. */
    @Test
    fun `a group with no subject reports none rather than guessing`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val id = env.groupRepo.save(Group(name = "Вечер", price = Money.ofMajor(25)), setOf(anya))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.subjectName)
    }

    @Test
    fun `the roster lists members in the app's name order, archived ones last`() = runTest(mainRule.dispatcher) {
        val yaroslav = student("Ярослав")
        val anna = student("анна")
        val boris = env.studentDao.upsert(StudentEntity(name = "Борис", archived = true))
        val id = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(25)),
            setOf(yaroslav, anna, boris),
        )

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
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
        val anya = student("Аня")
        val boris = student("Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya, boris))
        // Two lessons; Boris was only in the first.
        groupClass(id, anya, algebra, startMillis = 1_000)
        groupClass(id, boris, algebra, startMillis = 1_000)
        groupClass(id, anya, algebra, startMillis = 2_000)

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val attended = vm.state.value.members.associate { it.student.name to it.attended }
        assertEquals(2, attended["Аня"])
        assertEquals(1, attended["Борис"])
    }

    @Test
    fun `a member who has never attended is still listed, at zero`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(1, vm.state.value.members.size)
        assertEquals(0, vm.state.value.members.single().attended)
    }

    @Test
    fun `lessons are one row per session, newest first, charging every member`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val boris = student("Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya, boris))
        groupClass(id, anya, algebra, startMillis = 1_000)
        groupClass(id, boris, algebra, startMillis = 1_000)
        groupClass(id, anya, algebra, startMillis = 2_000)
        groupClass(id, boris, algebra, startMillis = 2_000)

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val lessons = vm.state.value.lessons
        assertEquals("four classes are two lessons", 2, lessons.size)
        assertEquals("newest first", 2_000L, lessons.first().session.start.toEpochMilli())
        assertEquals(2, lessons.first().attendees)
        assertEquals("both members' charges", Money.ofMajor(40), lessons.first().charge)
    }

    /**
     * The stat row's numbers are the tutor's, not the members': two lessons of an hour each is two
     * hours however many people were in the room, while the money is every member's charge.
     */
    @Test
    fun `the stats count the tutor's lessons and hours but every member's money`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val boris = student("Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya, boris))
        groupClass(id, anya, algebra, startMillis = 1_000, minutes = 90)
        groupClass(id, boris, algebra, startMillis = 1_000, minutes = 90)
        groupClass(id, anya, algebra, startMillis = 2_000, minutes = 60, status = "SCHEDULED")
        groupClass(id, boris, algebra, startMillis = 2_000, minutes = 60, status = "SCHEDULED")

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val totals = vm.state.value.totals
        assertEquals(1, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(90, totals.minutes)
        assertEquals(Money.ofMajor(40), totals.earned)
    }

    /** Another group's lessons must not appear here, however similar their times. */
    @Test
    fun `only this group's lessons are counted`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val ours = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya))
        val theirs = env.groupRepo.save(Group(name = "11 «Б»", price = Money.ofMajor(20)), setOf(anya))
        groupClass(ours, anya, algebra, startMillis = 1_000)
        groupClass(theirs, anya, algebra, startMillis = 2_000)
        // An individual class of the same student is not the group's either.
        env.classDao.upsert(
            ClassEntity(
                studentId = anya, subjectId = algebra, startMillis = 3_000, durationMinutes = 60,
                status = "COMPLETED", chargeMinor = Money.ofMajor(50).minor,
            ),
        )

        val vm = vm(ours)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(1, vm.state.value.lessons.size)
        assertEquals(Money.ofMajor(20), vm.state.value.totals.earned)
    }

    @Test
    fun `the card carries the group's rate history, newest first`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya))
        env.groupRepo.save(Group(id = id, name = "11 «А»", price = Money.ofMajor(25)), setOf(anya))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val changes = vm.state.value.rateHistory
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertTrue("the oldest row is where the rate started", changes.last().isInitial)
    }

    @Test
    fun `a group with no lessons reports empty totals`() = runTest(mainRule.dispatcher) {
        val anya = student("Аня")
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(anya))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.lessons.isEmpty())
        assertEquals(0, vm.state.value.totals.lessons)
        assertEquals(Money.ZERO, vm.state.value.totals.earned)
    }
}
