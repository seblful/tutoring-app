package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * The user-editable fields of a class. Everything else — status and the recognized [charge] — is
 * *derived* from these (and the new start) by [ClassRules.edit], never set by the caller, so an
 * edit can't leave a class with a charge that no longer matches its price/duration/status.
 */
data class ClassEdit(
    val studentId: Long,
    val subjectId: Long,
    val durationMinutes: Int,
    val price: Money,
    val mode: ClassMode,
)

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
     * Re-derives [TutoringClass.charge] so it stays consistent with the class's current price,
     * duration and status: a billable (completed) class is re-billed at its hourly [TutoringClass.price]
     * scaled by [TutoringClass.durationMinutes]; any non-billable status carries no charge.
     *
     * Call this after editing a class's details so a completed class's recognized income tracks the
     * edit instead of keeping a stale snapshot — recomputing the charge from the (possibly new)
     * price and duration also re-attributes it correctly when the student or subject changes,
     * since the new charge is derived from the current values rather than carried over.
     */
    fun rebill(c: TutoringClass): TutoringClass =
        c.copy(charge = if (c.status.isBillable) chargeFor(c.price, c.durationMinutes) else Money.ZERO)

    /**
     * A scheduled class auto-completes once its end time is at or before [now].
     * The app cannot see attendance, so a finished class is assumed to have happened.
     */
    fun shouldAutoComplete(c: TutoringClass, now: Instant): Boolean =
        c.status == ClassStatus.SCHEDULED && !c.end.isAfter(now)

    /**
     * Applies a status change, capturing or voiding the charge.
     *
     * Completing bills the class's booked **hourly rate** ([TutoringClass.price]) scaled by its
     * duration (see [chargeFor]) — so a 90-minute lesson at 30/h bills 45 — and the rate chosen
     * when it was booked means a later edit to the student's price never rewrites a completed class.
     * Any non-billable status voids the charge back to zero.
     *
     * @throws IllegalArgumentException if the transition is not allowed.
     */
    fun applyStatus(c: TutoringClass, to: ClassStatus): TutoringClass {
        require(canTransition(c.status, to)) { "Illegal transition ${c.status} -> $to" }
        return rebill(c.copy(status = to))
    }

    /**
     * The amount billed for a lesson of [durationMinutes] at [hourlyRate], rounded to the nearest
     * kopeck (half up). E.g. 30/h for 90 min → 45; for 30 min → 15.
     *
     * Floor division, not Kotlin's `/`. `/` truncates towards zero, which rounds *up* in magnitude
     * below zero and so is only half-up while the rate is positive — a credit of −0,005 would have
     * come back as −0,01 rather than −0,00, and the documented rule would have quietly meant two
     * different things either side of zero. A negative rate should not reach here at all (the money
     * inputs refuse one), which is exactly why the arithmetic should be right rather than defended:
     * this is the one line that turns a rate into money owed.
     */
    fun chargeFor(hourlyRate: Money, durationMinutes: Int): Money =
        Money(Math.floorDiv(hourlyRate.minor * durationMinutes + 30, 60L))

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

    /**
     * Applies an edit to [original] and resolves it to a billing-consistent state in one step:
     * the new fields ([changes]) are set, the new start goes through [move] (shifting it into the
     * future re-plans the class), then [rebill] re-derives the charge from the (possibly new)
     * price/duration/status. This is the only correct way to apply a field edit — it bundles the
     * move-and-rebill obligation so no caller can persist a class with a stale charge (e.g. change
     * a completed class's price but forget to re-bill it).
     */
    fun edit(original: TutoringClass, changes: ClassEdit, newStart: Instant, now: Instant): TutoringClass {
        val edited = original.copy(
            studentId = changes.studentId,
            subjectId = changes.subjectId,
            durationMinutes = changes.durationMinutes,
            price = changes.price,
            mode = changes.mode,
        )
        return rebill(move(edited, newStart, now))
    }
}
