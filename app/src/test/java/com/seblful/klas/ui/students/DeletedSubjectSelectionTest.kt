package com.seblful.klas.ui.students

import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeletedSubjectSelectionTest {
    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    @Test
    fun `deleting a selected subject cannot leave an invisible unsaveable enrollment`() = runTest(main.dispatcher) {
        val removed = env.fixtures.subject("Math")
        val kept = env.fixtures.subject("Physics")
        val vm = main.own(
            StudentFormViewModel(
                androidx.lifecycle.SavedStateHandle(),
                env.subjectRepo, env.enrollmentRepo, env.studentRepo, env.settingsRepo, main.appScope,
                main.dispatcher,
            ),
        )
        observing(vm.state)
        vm.update { it.copy(name = "Anna") }
        vm.toggleSubject(removed, true)
        vm.setPriceText(removed, "30")
        advanceUntilIdle()
        assertTrue(vm.state.value.canSave)

        env.subjectRepo.delete(env.subjectRepo.getById(removed)!!)
        advanceUntilIdle()

        assertFalse(vm.state.value.canSave)
        assertTrue(vm.state.value.selectedSubjectIds.isEmpty())
        vm.toggleSubject(kept, true)
        vm.setPriceText(kept, "50")
        advanceUntilIdle()
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(listOf(kept), env.studentDao.getSubjectIds(env.stored().students.single().id))
    }
}
