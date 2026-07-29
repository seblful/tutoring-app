package com.seblful.klas.work

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.Reminders
import java.time.Clock
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
    private val clock: Clock,
) {
    /**
     * Runs one upkeep pass. [now] defaults to the **injected** clock, never the wall clock: the
     * default is the path production takes, and a default a test cannot move makes the tested path
     * and the shipped path two different paths.
     */
    suspend fun run(now: Instant = clock.instant()) {
        val s = settings.get()
        // Auto-completion (and therefore auto-billing) is opt-in; when off, the tutor confirms each
        // lesson by marking it completed.
        if (s.autoCompleteEnabled) classes.autoCompleteDue(now)
        // The horizon is named once and used twice — to fetch the window and to filter it — because a
        // plan derived over a shorter window than it filters on would quietly drop reminders.
        val horizon = Reminders.DEFAULT_HORIZON
        scheduler.schedule(
            Reminders.plan(
                now = now,
                // Only the classes the plan could keep, not every class ever taught. This runs at
                // every app start, on every periodic tick and behind every class write, and it used to
                // read (and map, and parse the status text of) the tutor's whole history each time to
                // decide about the coming week. `plan` still applies the real filter — the window is
                // deliberately a superset of it.
                classes = classes.startingWithin(now, horizon),
                lead = Duration.ofMinutes(s.reminderLeadMinutes.toLong()),
                horizon = horizon,
            ),
        )
    }
}
