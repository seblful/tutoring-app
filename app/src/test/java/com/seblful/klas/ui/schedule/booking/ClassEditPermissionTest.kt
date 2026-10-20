package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditPermissionTest : ClassEditTestFixture() {

    private fun TestScope.asksOf(vm: ClassEditViewModel): List<Unit> {
        val asked = mutableListOf<Unit>()
        backgroundScope.launch { vm.askForNotifications.stream.collect { asked += it } }
        return asked
    }

    @Test
    fun `the first booking asks to be allowed to show reminders`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val asked = asksOf(vm)
        vm.fillFor(ann, subject, Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY), LocalTime(10, 0), 60)
        vm.save {}
        advanceUntilIdle()

        assertEquals(1, asked.size)
    }

    @Test
    fun `a later booking does not ask again`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val (ann, subject) = seedStudentWithPricedSubject()
        val date = Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY)
        seedLesson(date, LocalTime(8, 0))

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        val asked = asksOf(vm)
        vm.fillFor(ann, subject, date, LocalTime(10, 0), 60)
        vm.save {}
        advanceUntilIdle()

        assertTrue(
            "the schedule was not empty, so this is not the first lesson",
            asked.isEmpty(),
        )
    }

    @Test
    fun `a granted permission is not asked for again`() = runTest(mainRule.dispatcher) {
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val asked = asksOf(vm)
        vm.fillFor(ann, subject, Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY), LocalTime(10, 0), 60)
        vm.save {}
        advanceUntilIdle()

        assertTrue(asked.isEmpty())
    }

    @Test
    fun `reminders switched off ask for nothing`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        env.settingsRepo.update { it.copy(notificationsEnabled = false) }
        val (ann, subject) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val asked = asksOf(vm)
        vm.fillFor(ann, subject, Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY), LocalTime(10, 0), 60)
        vm.save {}
        advanceUntilIdle()

        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a first booking does not ask when the platform has no prompt to show`() =
        runTest(mainRule.dispatcher) {
            env.notificationPermission.granted = false
            env.notificationPermission.canAsk = false
            val (ann, subject) = seedStudentWithPricedSubject()
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            val asked = asksOf(vm)
            vm.fillFor(ann, subject, Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY), LocalTime(10, 0), 60)
            vm.save {}
            advanceUntilIdle()

            assertTrue(asked.isEmpty())
        }
}
