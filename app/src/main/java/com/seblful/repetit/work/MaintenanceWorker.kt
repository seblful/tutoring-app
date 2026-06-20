package com.seblful.repetit.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.domain.Reminders
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
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
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        val s = settings.get()
        // Auto-completion (and therefore auto-billing) is opt-out; when off, the tutor confirms
        // each lesson by marking it completed.
        if (s.autoCompleteEnabled) classes.autoCompleteDue(now)
        scheduler.scheduleReminders(
            Reminders.plan(now, classes.getAll(), lead = Duration.ofMinutes(s.reminderLeadMinutes.toLong())),
        )
        return Result.success()
    }
}
