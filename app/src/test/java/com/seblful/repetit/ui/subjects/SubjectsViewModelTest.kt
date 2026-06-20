package com.seblful.repetit.ui.subjects

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
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

    private fun vm() = SubjectsViewModel(env.subjectRepo)

    @Test
    fun `save trims the name and ignores a blank one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.subjects.collect {} }

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
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        vm.delete(com.seblful.repetit.domain.model.Subject(id = id, name = "Математика"))
        advanceUntilIdle()

        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    @Test
    fun `deleting a subject that still has classes is blocked and emits an event`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = 1, subjectId = id, startMillis = 1, durationMinutes = 60, status = "COMPLETED"))
        val vm = vm()
        val events = mutableListOf<SubjectsViewModel.Event>()
        backgroundScope.launch { vm.subjects.collect {} }
        backgroundScope.launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.delete(com.seblful.repetit.domain.model.Subject(id = id, name = "Математика"))
        advanceUntilIdle()

        assertEquals(listOf(SubjectsViewModel.Event.DELETE_BLOCKED_HAS_CLASSES), events)
        assertEquals(1, env.subjectDao.items.value.size) // not deleted
    }
}
