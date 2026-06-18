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
     * Whether a class may have its details edited or be moved to a new time. Only a class that
     * will still happen ([ClassStatus.SCHEDULED]) is mutable: a completed class has already
     * snapshotted its charge, so editing its student/subject would mis-attribute that income and
     * moving it would relocate it to another month. Corrections go through the status transitions
     * (cancel, which voids the charge, then return to schedule) instead.
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
     * Completing captures [currentPrice] as the class's charge — this snapshot is what
     * makes price edits never affect already-completed classes. Any non-billable status
     * voids the charge back to zero.
     *
     * @throws IllegalArgumentException if the transition is not allowed.
     */
    fun applyStatus(c: TutoringClass, to: ClassStatus, currentPrice: Money): TutoringClass {
        require(canTransition(c.status, to)) { "Illegal transition ${c.status} -> $to" }
        val charge = if (to.isBillable) currentPrice else Money.ZERO
        return c.copy(status = to, charge = charge)
    }

    /** Moves a class to [newStart], keeping it scheduled. */
    fun move(c: TutoringClass, newStart: Instant): TutoringClass =
        c.copy(start = newStart)
}
