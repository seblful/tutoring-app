package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import java.time.Instant

/** Rules governing a class's status: transitions, billing on completion, auto-completion. */
object ClassRules {

    /**
     * Status changes the tutor (or the auto-complete job) may apply. A status is never listed as a
     * transition to itself, so [availableTransitions] only ever offers a *different* status.
     */
    private val allowed: Map<ClassStatus, Set<ClassStatus>> = mapOf(
        ClassStatus.SCHEDULED to setOf(ClassStatus.COMPLETED, ClassStatus.CANCELLED),
        // A wrongly auto-completed class can be cancelled (which voids its charge); it is not sent
        // back to "scheduled" — returning to the schedule is offered only for cancelled classes.
        ClassStatus.COMPLETED to setOf(ClassStatus.CANCELLED),
        ClassStatus.CANCELLED to setOf(ClassStatus.SCHEDULED),
    )

    fun canTransition(from: ClassStatus, to: ClassStatus): Boolean =
        from == to || to in (allowed[from] ?: emptySet())

    /**
     * The statuses [from] may be changed to, excluding [from] itself. Drives the class actions
     * menu, which must never offer the status the class is already in.
     */
    fun availableTransitions(from: ClassStatus): Set<ClassStatus> = allowed[from] ?: emptySet()

    /**
     * Whether a class may have its details (student/subject/price) edited. Only a class that
     * will still happen ([ClassStatus.SCHEDULED]) is editable: a completed class has already
     * snapshotted its charge, so editing its student/subject would mis-attribute that income.
     * Corrections go through the status transitions (cancel, which voids the charge, then return
     * to schedule) instead. Note that *moving* (rescheduling) is allowed for any status — see
     * [move] — because moving to the future re-plans the class.
     */
    fun isMutable(status: ClassStatus): Boolean = status == ClassStatus.SCHEDULED

    /**
     * A scheduled class auto-completes once its end time is at or before [now].
     * The app cannot see attendance, so a finished class is assumed to have happened.
     */
    fun shouldAutoComplete(c: TutoringClass, now: Instant): Boolean =
        c.status == ClassStatus.SCHEDULED && !c.end.isAfter(now)

    /**
     * Applies a status change, capturing or voiding the charge.
     *
     * Completing bills the class's own [TutoringClass.price] — the price chosen when it was
     * booked — so a later edit to the student's price never rewrites an already-completed class.
     * Any non-billable status voids the charge back to zero.
     *
     * @throws IllegalArgumentException if the transition is not allowed.
     */
    fun applyStatus(c: TutoringClass, to: ClassStatus): TutoringClass {
        require(canTransition(c.status, to)) { "Illegal transition ${c.status} -> $to" }
        val charge = if (to.isBillable) c.price else Money.ZERO
        return c.copy(status = to, charge = charge)
    }

    /**
     * Reschedules a class to [newStart].
     *
     * - Moving it into the **future** (relative to [now]) re-plans it: the status becomes
     *   [ClassStatus.SCHEDULED] and any captured [TutoringClass.charge] is voided, since a class
     *   that will still happen hasn't been billed yet. This lets a completed/cancelled class be
     *   put back on the schedule by moving it forward.
     * - Moving it into the **past** keeps the status (and charge) untouched — e.g. correcting the
     *   recorded time of a class that already happened must not silently change whether it billed.
     */
    fun move(c: TutoringClass, newStart: Instant, now: Instant): TutoringClass =
        if (newStart.isAfter(now)) {
            c.copy(start = newStart, status = ClassStatus.SCHEDULED, charge = Money.ZERO)
        } else {
            c.copy(start = newStart)
        }
}
