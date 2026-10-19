package com.seblful.klas.domain.reminders

/**
 * Outbound port for the time-driven side effects of a class changing: auto-completion and reminder
 * notifications. Lets the data layer and the upkeep pass trigger scheduling without depending on
 * WorkManager. Implemented in the `work` package.
 *
 * [refreshNow] is the *request* — "something changed, work the plan out again" — made by writers that
 * have no idea what the new plan is. [schedule] is the *result*, handed over once by whatever
 * recomputed it. Keeping both on the port is what lets the recomputation itself (`work/Maintenance`)
 * be ordinary code rather than something only a device can run.
 */
interface ReminderScheduler {

    /** Re-derive the whole plan as soon as possible. Idempotent; safe to call on every write. */
    fun refreshNow()

    /**
     * Make sure the recurring upkeep pass is arranged. Idempotent — safe to call on every app start.
     *
     * The standing form of [refreshNow]: the same pass, on a timer, so a phone whose owner does not open
     * the app still auto-completes finished lessons and re-derives the reminder plan. It is on the port
     * for the reason the other two are — `KlasApp` was injecting the WorkManager adapter by its concrete
     * class to reach it, which made the app's entry point the one place naming a `work/` implementation.
     */
    fun ensurePeriodicMaintenance()

    /**
     * Arms future entries in [plans] and preserves existing alarms for due entries.
     * Reminders absent from the plan are cancelled. An empty list disarms them all.
     */
    fun schedule(plans: List<Reminders.Plan>)
}
