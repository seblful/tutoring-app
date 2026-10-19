package com.seblful.klas.format

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.days
import com.seblful.klas.ui.schedule.ScheduleViewModel
import com.seblful.klas.work.ClockChangeReceiver
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ZoneChangeFlowsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private val instant = Instant.parse("2026-06-01T00:30:00Z")

    @Test
    fun `today wakes on a zone change instead of waiting for the old midnight`() = runTest(main.dispatcher) {
        inUtc { context, clock ->
            val days = mutableListOf<LocalDate>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { clock.days().collect { days += it } }
            runCurrent()

            changeToNewYork(context)
            runCurrent()

            assertEquals(listOf(LocalDate(2026, 6, 1), LocalDate(2026, 5, 31)), days)
        }
    }

    @Test
    fun `an open schedule requeries zone bounds and moves lessons in the current zone`() = runTest(main.dispatcher) {
        inUtc { context, clock ->
            val subject = env.fixtures.subject()
            val student = env.fixtures.pricedStudent(subjectId = subject)
            val id = env.fixtures.lesson(subject, listOf(student), instant).single()
            val vm = ScheduleViewModel(
                env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo,
                main.appScope, main.dispatcher, clock,
            )
            observing(vm.content)
            runCurrent()
            assertEquals(1, vm.content.current.sections.size)

            changeToNewYork(context)
            runCurrent()

            assertTrue("The lesson is now on Sunday, outside the selected week", vm.content.current.sections.isEmpty())
            vm.moveTo(env.classRepo.getSessionFor(id)!!, LocalDate(2026, 6, 2), LocalTime(10, 0))
            runCurrent()
            assertEquals(Instant.parse("2026-06-02T14:00:00Z"), env.classRepo.getById(id)!!.start)
        }
    }

    private suspend fun inUtc(block: suspend (Context, DeviceClock) -> Unit) {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val context = ApplicationProvider.getApplicationContext<Context>()
            block(context, DeviceClock(context, object : Clock { override fun now(): Instant = instant }))
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

    private fun changeToNewYork(context: Context) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
        // Delivered by hand rather than broadcast, to every receiver of the action but the manifest's
        // `ClockChangeReceiver`: it is a Hilt component, which this suite's bare `Application` cannot inject,
        // and only the flows' own runtime receiver is under test here.
        val changed = Intent(Intent.ACTION_TIMEZONE_CHANGED)
        shadowOf(context as Application).registeredReceivers
            .filter { it.intentFilter.hasAction(changed.action) && it.broadcastReceiver !is ClockChangeReceiver }
            .forEach { it.broadcastReceiver.onReceive(context, changed) }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
