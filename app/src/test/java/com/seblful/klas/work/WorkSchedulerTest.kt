package com.seblful.klas.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.seblful.klas.notifications.ReminderAlarms
import com.seblful.klas.testutil.FakeArmedReminders
import com.seblful.klas.testutil.FakeExactAlarmPermission
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.RecordingAlarmClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class WorkSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // WorkManager reports arranged times from the system clock, so the scheduler's clock agrees with it.
    private val clock = MutableClock(Clock.System.now())
    private lateinit var scheduler: WorkScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val alarms =
            ReminderAlarms(RecordingAlarmClock(), FakeArmedReminders(), FakeExactAlarmPermission(granted = true))
        scheduler = WorkScheduler(context, alarms, clock)
    }

    private fun arranged(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `the periodic pass runs hours apart`() {
        scheduler.ensurePeriodicMaintenance()

        val info = arranged(WorkScheduler.PERIODIC_MAINTENANCE).single()
        assertEquals(WorkScheduler.PERIODIC_HOURS.hours.inWholeMilliseconds, info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `the weekly backup is looked at daily`() {
        scheduler.ensurePeriodicMaintenance()

        val info = arranged(WorkScheduler.AUTO_BACKUP).single()
        assertEquals(1.days.inWholeMilliseconds, info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `an install arranged under an older interval takes the new one`() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WorkScheduler.PERIODIC_MAINTENANCE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(30, TimeUnit.MINUTES).build(),
        )

        scheduler.ensurePeriodicMaintenance()

        val info = arranged(WorkScheduler.PERIODIC_MAINTENANCE).single()
        assertEquals(WorkScheduler.PERIODIC_HOURS.hours.inWholeMilliseconds, info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `the next pass waits until the instant asked for`() = runTest {
        scheduler.scheduleNextPass(clock.now() + 50.minutes)

        assertEquals(50.minutes.inWholeMilliseconds, arranged(WorkScheduler.NEXT_PASS).single().initialDelayMillis)
    }

    @Test
    fun `asking for the same instant again leaves the arranged pass alone`() = runTest {
        val at = clock.now() + 50.minutes
        scheduler.scheduleNextPass(at)
        val first = arranged(WorkScheduler.NEXT_PASS).single().id

        clock.advanceBy(5_000)
        scheduler.scheduleNextPass(at)

        assertEquals(first, arranged(WorkScheduler.NEXT_PASS).single().id)
    }

    @Test
    fun `an earlier lesson end replaces the arranged pass`() = runTest {
        scheduler.scheduleNextPass(clock.now() + 50.minutes)

        scheduler.scheduleNextPass(clock.now() + 20.minutes)

        assertEquals(20.minutes.inWholeMilliseconds, arranged(WorkScheduler.NEXT_PASS).single().initialDelayMillis)
    }

    @Test
    fun `no lesson to settle cancels the arranged pass`() = runTest {
        scheduler.scheduleNextPass(clock.now() + 50.minutes)

        scheduler.scheduleNextPass(null)

        assertEquals(emptyList<WorkInfo>(), arranged(WorkScheduler.NEXT_PASS))
    }
}
