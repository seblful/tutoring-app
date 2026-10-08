package com.seblful.klas.domain

/**
 * Arranges for the app's digests to happen — the seam between deciding *when* ([Digests.plan]) and asking
 * the platform to do it.
 *
 * A port for the same reason [ReminderScheduler] is one: the deciding is pure and belongs where a JVM test
 * can drive it, and everything below this line is WorkManager. It is a **separate** port rather than two
 * more methods on `ReminderScheduler` because the two schedule different kinds of thing — a reminder is an
 * alarm that must not be late, a digest is deferrable work — and that distinction is the app's main
 * argument about its own notifications. One interface holding both would file them under the same idea.
 */
interface DigestScheduler {

    /**
     * Replaces whatever is arranged with exactly [plan]: each non-null instant is scheduled, and each null
     * one has anything already arranged for it cancelled.
     *
     * Replace rather than diff, as `ReminderAlarms.set` does and for the same reason — the plan is
     * authoritative and tiny, while a diff would have to be right about *why* two plans differ. The one
     * exception is a digest that is **due and has not happened yet**, which is left to happen:
     * [Digests.stillOwed] states that rule and says what it cost to be missing.
     *
     * `suspend` because an adapter has to ask its scheduler what is currently arranged before it can honour
     * that exception, and the platform's answer is asynchronous. Every caller is already in a coroutine —
     * `Maintenance.run` is the only one.
     */
    suspend fun schedule(plan: Digests.Plan)
}
