package com.seblful.klas.ui.settings

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsAutoCompleteTest : SettingsTestFixture() {

    private suspend fun finishedLessons(count: Int): List<Long> {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        val ended = env.clock.now().minus((30L * 24 * 3600).seconds)
        return (1..count).map { n ->
            env.fixtures.lesson(
                subjectId = subjectId,
                students = listOf(studentId),
                start = ended.plus((n * 2L * 3600).seconds),
            ).single()
        }
    }

    private suspend fun statuses(ids: List<Long>) = ids.map { env.classRepo.getById(it)?.status }

    @Test
    fun `a backlog of any size is asked about before anything is stored`() =
        runTest(mainRule.dispatcher) {
            val ids = finishedLessons(3)

            val vm = vm()
            vm.setAutoCompleteEnabled(true)
            advanceUntilIdle()

            assertEquals(3, vm.pendingSweep.value)
            assertFalse("the switch stays off while the question is up", env.settingsRepo.get().autoCompleteEnabled)
            assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
        }

    @Test
    fun `confirming the question turns it on and settles the backlog`() = runTest(mainRule.dispatcher) {
        val ids = finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        vm.confirmPendingSweep()
        advanceUntilIdle()

        assertNull(vm.pendingSweep.value)
        assertTrue(env.settingsRepo.get().autoCompleteEnabled)
        assertEquals(List(3) { LessonStatus.COMPLETED }, statuses(ids))
    }

    @Test
    fun `and says how many it settled`() = runTest(mainRule.dispatcher) {
        finishedLessons(3)
        val vm = vm()
        val reported = mutableListOf<Int>()
        backgroundScope.launch { vm.swept.stream.collect { reported += it } }

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()
        vm.confirmPendingSweep()
        advanceUntilIdle()

        assertEquals(listOf(3), reported)
    }

    @Test
    fun `nothing waiting means nothing to ask`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val reported = mutableListOf<Int>()
        backgroundScope.launch { vm.swept.stream.collect { reported += it } }

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        assertNull("no question", vm.pendingSweep.value)
        assertTrue("the setting simply goes on", env.settingsRepo.get().autoCompleteEnabled)
        assertTrue("and nothing is reported", reported.isEmpty())
    }

    @Test
    fun `declining leaves the switch off and the lessons alone`() = runTest(mainRule.dispatcher) {
        val ids = finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        vm.dismissPendingSweep()
        advanceUntilIdle()

        assertNull(vm.pendingSweep.value)
        assertFalse(env.settingsRepo.get().autoCompleteEnabled)
        assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
    }

    @Test
    fun `declining and trying again asks again`() = runTest(mainRule.dispatcher) {
        finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()
        vm.dismissPendingSweep()

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        assertEquals(3, vm.pendingSweep.value)
    }

    @Test
    fun `turning auto-completion off settles nothing`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }
        val ids = finishedLessons(3)

        val vm = vm()
        vm.setAutoCompleteEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().autoCompleteEnabled)
        assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
    }
}
