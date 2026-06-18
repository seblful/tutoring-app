package com.seblful.tutoringapp.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.domain.Reminders
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Periodic upkeep: auto-completes every scheduled class whose end time has passed (capturing
 * its then-current price), then re-derives the reminder jobs for what's still upcoming.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val classes: ClassRepository,
    private val scheduler: WorkScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        classes.autoCompleteDue(now)
        scheduler.scheduleReminders(Reminders.plan(now, classes.getAll()))
        return Result.success()
    }
}
