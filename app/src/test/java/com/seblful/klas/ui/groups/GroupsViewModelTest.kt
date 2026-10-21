package com.seblful.klas.ui.groups

import com.seblful.klas.domain.model.Group
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.people.LessonLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupsViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    private fun vm() = mainRule.own(
        GroupsViewModel(
            env.groupRepo, env.studentRepo, env.subjectRepo, env.classRepo, env.clock, mainRule.dispatcher,
        ),
    )

    @Test
    fun `rows list each group with its members' names`() = runTest(mainRule.dispatcher) {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        env.groupRepo.save(Group(name = "Выпускники"), setOf(bob))

        val vm = vm()
        observing(vm.rows)
        runCurrent()

        assertEquals(listOf("11 «А»", "Выпускники"), vm.rows.current.map { it.group.name })
        assertEquals(listOf("Аня", "Борис"), vm.rows.current.first().memberNames)
        assertEquals(listOf("Борис"), vm.rows.current.last().memberNames)
    }

    @Test
    fun `a group's next lesson is its own, not one a member has alone`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", maths)
        val group = env.fixtures.group("11 «А»", maths, setOf(ann))
        env.fixtures.group("Выпускники", maths)
        // The clock reads 15:00 on Monday 1 June in Minsk; Аня's own lesson is sooner than the group's.
        env.fixtures.lesson(maths, listOf(ann), Instant.parse("2026-06-02T07:00:00Z"))
        env.fixtures.lesson(maths, listOf(ann), Instant.parse("2026-06-05T15:00:00Z"), groupId = group)

        val vm = vm()
        observing(vm.rows)
        runCurrent()

        val rows = vm.rows.current.associateBy { it.group.name }
        assertEquals(
            LessonLine.Next(LocalDate(2026, 6, 5), LocalTime(18, 0), daysAway = 4, thisYear = 2026),
            rows.getValue("11 «А»").lessons,
        )
        assertEquals(LessonLine.Never, rows.getValue("Выпускники").lessons)
    }

    @Test
    fun `archived groups appear only when the archive is shown`() = runTest(mainRule.dispatcher) {
        val ann = env.fixtures.student(name = "Аня")
        val active = env.groupRepo.save(Group(name = "Активная"), setOf(ann))
        val retired = env.groupRepo.save(Group(name = "Прошлый год"), setOf(ann))
        env.groupRepo.setArchived(env.groupRepo.getById(retired)!!, true)

        val vm = vm()
        observing(vm.rows)
        runCurrent()

        assertEquals(listOf(active), vm.rows.current.map { it.group.id })

        vm.toggleArchived()
        runCurrent()
        assertEquals(setOf(active, retired), vm.rows.current.map { it.group.id }.toSet())
    }

    /**
     * The roster order used to be `sortedBy { it.name }` — no case folding — while every other listing
     * of people went through `NameOrder`. In a Russian-language app that is not a subtle difference:
     * every capital Cyrillic letter has a lower code point than every small one, so a single
     * lower-case name sent the whole roster out of order. «Ярослав» sorted before «анна».
     */
    @Test
    fun `a roster is case-folded, so a lower-case name does not sort last`() = runTest(mainRule.dispatcher) {
        val yaroslav = env.fixtures.student(name = "Ярослав")
        val anna = env.fixtures.student(name = "анна")
        env.groupRepo.save(Group(name = "11 «А»"), setOf(yaroslav, anna))

        val vm = vm()
        observing(vm.rows)
        runCurrent()

        assertEquals(listOf("анна", "Ярослав"), vm.rows.current.single().memberNames)
    }
}
