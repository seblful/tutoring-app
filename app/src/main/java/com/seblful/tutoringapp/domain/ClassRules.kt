package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import java.time.Instant

/** Rules governing a class's status: transitions, billing on completion, auto-completion. */
object ClassRules {

    /** Status changes the tutor (or the auto-complete job) may apply. */
    private val allowed: Map<ClassStatus, Set<ClassStatus>> = mapOf(
        ClassStatus.SCHEDULED to setOf(ClassStatus.COMPLETED, ClassStatus.CANCELLED, ClassStatus.MISSED),
        // Corrections after the clock auto-completed a class the student didn't attend.
        ClassStatus.COMPLETED to setOf(ClassStatus.MISSED, ClassStatus.CANCELLED),
        ClassStatus.MISSED to setOf(ClassStatus.COMPLETED, ClassStatus.SCHEDULED),
        ClassStatus.CANCELLED to setOf(ClassStatus.SCHEDULED),
    )

    fun canTransition(from: ClassStatus, to: ClassStatus): Boolean =
        from == to || to in (allowed[from] ?: emptySet())

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
