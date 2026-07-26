package com.seblful.klas.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.ReminderAlarms
import com.seblful.klas.domain.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's time-driven scheduling, split by what each half actually needs.
 *
 * [refreshNow] — "something changed, work the plan out again" — is deferrable by nature: it is bookkeeping,
 * nobody is waiting on it, and running a minute late costs nothing. That is WorkManager's job, and it
 * stays here. The periodic [MaintenanceWorker] is the same argument on a timer: the safety net that
 * auto-completes due lessons and re-derives reminders even when the app is never opened.
 *
 * [schedule] is **not** deferrable, and used to be treated as though it were. It hands over the reminders
 * themselves, and a reminder is worthless late — so it goes to [ReminderAlarms] and the platform's alarm
 * clock instead of a batched worker. See that class for the whole argument.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: ReminderAlarms,
) : ReminderScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    /** Enqueues the recurring maintenance job. Idempotent — safe to call on every app start. */
    fun ensurePeriodicMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_MAINTENANCE,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Runs maintenance now — after a class changes or when the app opens. */
    override fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        workManager.enqueueUniqueWork(MAINTENANCE_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Replaces every armed reminder with exactly [plans] — as **alarms**, not as deferrable work.
     *
     * These used to be `OneTimeWorkRequest`s with an initial delay, which WorkManager treats as a lower
     * bound and is free to batch or defer under Doze. «Занятие через час» delivered two hours late is
     * not a late reminder, it is a wrong one — and `Reminders.shouldPost` would still post it, because
     * the lesson had not ended yet. Handing this to [ReminderAlarms] is what makes the promised time the
     * actual one.
     */
    override fun schedule(plans: List<Reminders.Plan>) {
        alarms.set(plans)
    }

    companion object {
        const val PERIODIC_MAINTENANCE = "maintenance-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
    }
}
