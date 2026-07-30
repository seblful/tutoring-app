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
     * How many lessons a pass at [now] **would** auto-complete — the backlog, and deliberately
     * regardless of whether the setting is on: this is what the settings screen puts to the tutor
     * before turning it on, and the sweep only follows if they say yes.
     *
     * Here rather than at the call site so it shares this class's clock with [run]. A backlog counted
     * against one instant and settled against another is a question answered about a different number.
     */
    suspend fun dueForCompletion(now: Instant = clock.instant()): Int =
        classes.countDueForCompletion(now)

    /**
     * Runs one upkeep pass, returning **how many lessons it auto-completed** — zero when the setting
     * is off, or when nothing had finished. Counted in lessons, like [dueForCompletion]: a group's
     * hour bills every member but is one lesson settled.
     *
     * The count is returned because a pass the tutor **asked for** has to be able to say what it did:
     * turning auto-completion on settles the whole backlog at once, and an income figure that moves
     * with nothing accounting for it is the thing that made this feature feel arbitrary.
     *
     * [now] defaults to the **injected** clock, never the wall clock: the default is the path
     * production takes, and a default a test cannot move makes the tested path and the shipped path
     * two different paths.
     */
    suspend fun run(now: Instant = clock.instant()): Int {
        val s = settings.get()
        // Auto-completion (and therefore auto-billing) is opt-in; when off, the tutor confirms each
        // lesson by marking it completed.
        val completed = if (s.autoCompleteEnabled) classes.autoCompleteDue(now) else emptyList()
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
        return completed.distinctBy { it.lessonId }.size
    }
}
