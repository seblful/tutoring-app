package com.seblful.klas.ui.groups

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

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

        assertEquals(listOf("11 «А»", "Выпускники"), vm.rows.value.map { it.group.name })
        assertEquals(listOf("Аня", "Борис"), vm.rows.value.first().memberNames)
        assertEquals(listOf("Борис"), vm.rows.value.last().memberNames)
    }

    @Test
    fun `archived groups appear only when the archive is shown`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val active = env.groupRepo.save(Group(name = "Активная"), setOf(ann))
        val retired = env.groupRepo.save(Group(name = "Прошлый год"), setOf(ann))
        env.groupRepo.setArchived(env.groupRepo.getById(retired)!!, true)

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(listOf(active), vm.rows.value.map { it.group.id })

        vm.toggleArchived()
        advanceUntilIdle()
        assertEquals(setOf(active, retired), vm.rows.value.map { it.group.id }.toSet())
    }
}
