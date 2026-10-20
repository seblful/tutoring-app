package com.seblful.klas.ui.groups

import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm() = GroupsViewModel(env.groupRepo, env.studentRepo, env.subjectRepo, mainRule.dispatcher)

    @Test
    fun `rows list each group with its members' names`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        env.groupRepo.save(Group(name = "Выпускники"), setOf(bob))

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(listOf("11 «А»", "Выпускники"), vm.rows.current.map { it.group.name })
        assertEquals(listOf("Аня", "Борис"), vm.rows.current.first().memberNames)
        assertEquals(listOf("Борис"), vm.rows.current.last().memberNames)
    }

    @Test
    fun `archived groups appear only when the archive is shown`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val active = env.groupRepo.save(Group(name = "Активная"), setOf(ann)).id
        val retired = env.groupRepo.save(Group(name = "Прошлый год"), setOf(ann)).id
        env.groupRepo.setArchived(env.groupRepo.getById(retired)!!, true)

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(listOf(active), vm.rows.current.map { it.group.id })

        vm.toggleArchived()
        advanceUntilIdle()
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
        val yaroslav = env.studentDao.upsert(StudentEntity(name = "Ярослав"))
        val anna = env.studentDao.upsert(StudentEntity(name = "анна"))
        env.groupRepo.save(Group(name = "11 «А»"), setOf(yaroslav, anna))

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(listOf("анна", "Ярослав"), vm.rows.current.single().memberNames)
    }
}
