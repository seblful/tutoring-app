package com.seblful.klas.domain.reminders

import kotlin.time.Instant

/**
 * Outbound port for the time-driven side effects of a class changing: auto-completion and reminder
 * notifications. Lets the data layer and the upkeep pass trigger scheduling without depending on
 * WorkManager. Implemented in the `work` package.
 *
 * [refreshNow] is the *request* — "something changed, work the plan out again" — made by writers that
 * have no idea what the new plan is. [schedule] is the *result*, handed over once by whatever
 * recomputed it. Keeping both on the port is what lets the recomputation itself (`maintenance/Maintenance`)
 * be ordinary code rather than something only a device can run.
 */
interface ReminderScheduler {

    /** Re-derive the whole plan as soon as possible. Idempotent; safe to call on every write. */
    fun refreshNow()

    /**
     * Arranges one extra pass at [at], replacing any earlier arrangement, or cancels it when null. The
     * periodic pass is hours apart; this one settles a finished lesson when auto-completion is on.
     */
    suspend fun scheduleNextPass(at: Instant?)

    /**
     * Arms future entries in [plans] and preserves existing alarms for due entries.
     * Reminders absent from the plan are cancelled. An empty list disarms them all.
     */
    fun schedule(plans: List<Reminders.Plan>)
}
