package com.seblful.repetit.domain

/**
 * Outbound port for the time-driven side effects of a class changing: auto-completion and reminder
 * notifications. Lets the data layer and the upkeep pass trigger scheduling without depending on
 * WorkManager. Implemented in the `work` package.
 *
 * The two methods are the two directions the app talks to the scheduler in. [refreshNow] is the
 * *request* — "something changed, work the plan out again" — made by writers that have no idea what
 * the new plan is. [schedule] is the *result*, handed over once by whatever recomputed it. Keeping
 * both on the port is what lets the recomputation itself (`work/Maintenance`) be ordinary code
 * rather than something only a device can run.
 */
interface ReminderScheduler {

    /** Re-derive the whole plan as soon as possible. Idempotent; safe to call on every write. */
    fun refreshNow()

    /** Replaces every armed reminder with exactly [plans]. An empty list disarms them all. */
    fun schedule(plans: List<Reminders.Plan>)
}
