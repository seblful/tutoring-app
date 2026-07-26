package com.seblful.klas.work

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.Reminders
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the periodic upkeep does, except being periodic: auto-complete the classes whose end
 * time has passed, then re-derive the reminder plan for what is still upcoming.
 *
 * This lives outside [MaintenanceWorker] for the same reason `DueReminder` lives outside
 * `ReminderWorker`: a `CoroutineWorker` can only be driven with a `Context` and `WorkerParameters`,
 * so anything inside one is reachable only from a device — which is how the decisions here came to
 * have no test. As ordinary code over the repositories and the [ReminderScheduler] port, a JVM test
 * drives all of it and the worker is left as WorkManager's adapter onto it.
 *
 * The order matters and is fixed here: auto-completion runs **first**, so a lesson that just ended
 * is completed before the plan is derived and therefore isn't given a reminder it no longer needs.
 */
@Singleton
class Maintenance @Inject constructor(
    private val classes: ClassRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    /** Runs one upkeep pass. [now] is a parameter so a test needn't depend on the wall clock. */
    suspend fun run(now: Instant = Instant.now()) {
        val s = settings.get()
        // Auto-completion (and therefore auto-billing) is opt-in; when off, the tutor confirms each
        // lesson by marking it completed.
        if (s.autoCompleteEnabled) classes.autoCompleteDue(now)
        scheduler.schedule(
            Reminders.plan(
                now = now,
                classes = classes.getAll(),
                lead = Duration.ofMinutes(s.reminderLeadMinutes.toLong()),
            ),
        )
    }
}
