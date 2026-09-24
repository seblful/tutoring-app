package com.seblful.klas.ui.subjects

import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = SubjectsViewModel(env.subjectRepo, mainRule.appScope, mainRule.dispatcher)

    @Test
    fun `save trims the name and ignores a blank one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.subjects)

        vm.save(id = 0, name = "   ")
        advanceUntilIdle()
        assertTrue(env.subjectDao.items.value.isEmpty())

        vm.save(id = 0, name = "  Математика  ")
        advanceUntilIdle()
        assertEquals(listOf("Математика"), env.subjectDao.items.value.map { it.name })
    }

    @Test
    fun `deleting a subject with no classes removes it`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.subjects)
        advanceUntilIdle()

        vm.delete(Subject(id = id, name = "Математика"))
        advanceUntilIdle()

        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    @Test
    fun `deleting a subject that still has classes is blocked and emits an event`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.fixtures.storedClass(studentId = 1, subjectId = id, startMillis = 1, durationMinutes = 60, status = "COMPLETED")
        val vm = vm()
        val events = mutableListOf<SubjectsViewModel.Event>()
        observing(vm.subjects)
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        advanceUntilIdle()

        vm.delete(Subject(id = id, name = "Математика"))
        advanceUntilIdle()

        assertEquals(listOf(SubjectsViewModel.Event.DELETE_BLOCKED_HAS_CLASSES), events)
        assertEquals(1, env.subjectDao.items.value.size) // not deleted
    }

    @Test
    fun `a name another subject already has is refused and said`() = runTest(mainRule.dispatcher) {
        env.fixtures.subject("Математика")
        val vm = vm()
        val events = mutableListOf<SubjectsViewModel.Event>()
        observing(vm.subjects)
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        advanceUntilIdle()

        vm.save(id = 0, name = " математика ")
        advanceUntilIdle()

        assertEquals(listOf(SubjectsViewModel.Event.NAME_TAKEN), events)
        assertEquals(1, env.subjectDao.items.value.size)
    }
}
