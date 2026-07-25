package com.seblful.repetit.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.seblful.repetit.domain.ReminderScheduler
import com.seblful.repetit.domain.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns all WorkManager scheduling. Reminders are precise one-shot jobs (one per upcoming
 * class); the periodic [MaintenanceWorker] is the safety net that auto-completes due classes
 * and re-derives reminders, so externally-made changes are picked up even without the app open.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    /** Replaces all reminder jobs with one per [plans] entry. */
    override fun schedule(plans: List<Reminders.Plan>) {
        workManager.cancelAllWorkByTag(TAG_REMINDER)
        for (plan in plans) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_CLASS_ID to plan.classId))
                .addTag(TAG_REMINDER)
                .build()
            workManager.enqueueUniqueWork(reminderName(plan.classId), ExistingWorkPolicy.REPLACE, request)
        }
    }

    private fun reminderName(classId: Long) = "reminder-$classId"

    companion object {
        const val PERIODIC_MAINTENANCE = "maintenance-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
        const val TAG_REMINDER = "class-reminder"
    }
}
