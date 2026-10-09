package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * The user-editable fields of a class. Everything else — status and the recognized [charge] — is
 * *derived* from these (and the new start) by [ClassRules.edit], never set by the caller, so an
 * edit can't leave a class with a charge that no longer matches its price/duration/status.
 *
 * Most of these are the **lesson's** shared fields carried per class ([subjectId], [durationMinutes] and
 * [mode] are all columns of `lessons`), which is what lets one edit fan out over a group's roster
 * without any class of it being able to disagree with the others.
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
    private val allowed: Map<LessonStatus, Set<LessonStatus>> = mapOf(
        LessonStatus.SCHEDULED to setOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED),
        // A wrongly auto-completed class can be cancelled (which voids its charge); it is not sent
        // back to "scheduled" — returning to the schedule is offered only for cancelled classes.
        LessonStatus.COMPLETED to setOf(LessonStatus.CANCELLED),
        LessonStatus.CANCELLED to setOf(LessonStatus.SCHEDULED),
    )

    fun canTransition(from: LessonStatus, to: LessonStatus): Boolean =
        from == to || to in (allowed[from] ?: emptySet())

    /**
     * The statuses [from] may be changed to, excluding [from] itself. Drives the class actions
     * menu, which must never offer the status the class is already in.
     */
    fun availableTransitions(from: LessonStatus): Set<LessonStatus> = allowed[from] ?: emptySet()

    /**
     * Re-derives [TutoringClass.charge] so it stays consistent with what the class now is: a billable one
     * — the lesson happened and this student owes for it ([TutoringClass.isBillable]) — is re-billed at
     * its hourly [TutoringClass.price] scaled by [TutoringClass.durationMinutes]; anything else carries
     * no charge.
     *
     * Call this after editing a class's details so a taught lesson's recognized income tracks the edit
     * instead of keeping a stale snapshot — recomputing the charge from the (possibly new) price and
     * duration also re-attributes it correctly when the student or subject changes, since the new charge
     * is derived from the current values rather than carried over. **And after a change of attendance**,
     * which is the other half of billable: marking somebody absent is what stops charging them.
     */
    fun rebill(c: TutoringClass): TutoringClass =
        c.copy(charge = if (c.isBillable) chargeFor(c.price, c.durationMinutes) else Money.ZERO)

    /**
     * A scheduled class auto-completes once its end time is at or before [now].
     * The app cannot see attendance, so a finished class is assumed to have happened.
     *
     * **Which is [LessonPhase.OVERDUE], and is asked that way.** "Finished, and still scheduled" is also
     * exactly what the evening digest counts (`Digests.Unfinished`), and the two used to be two
     * hand-written boundary comparisons: one of them off by an instant would have the notification
     * naming lessons the upkeep pass had already settled, or staying silent about ones it had not.
     */
    fun shouldAutoComplete(c: TutoringClass, now: Instant): Boolean =
        LessonPhase.of(c, now).isUnfinished

    /**
     * Applies a lesson's status change to one of its classes, capturing or voiding the charge — **and
     * carrying the attendance the new status implies**.
     *
     * Completing bills the class's booked **hourly rate** ([TutoringClass.price]) scaled by its
     * duration (see [chargeFor]) — so a 90-minute lesson at 30/h bills 45 — and the rate chosen when it
     * was booked means a later edit to the student's price never rewrites a taught lesson. Anything
     * non-billable voids the charge back to zero.
     *
     * The attendance moves with it, because the app cannot see who was in the room:
     * - completing turns everyone still merely **expected** into **present** — the tutor then corrects
     *   whoever was not there, which is the one thing the old model could not hold;
     * - un-completing (to scheduled or cancelled) turns **present** back into **expected**: an hour that
     *   is not recorded as having happened cannot have had anybody in it.
     *
     * A member already marked absent — either way — keeps that state through both, so a correction made
     * about one person survives a status change made about the whole lesson.
     *
     * @throws IllegalArgumentException if the transition is not allowed.
     */
    fun applyStatus(c: TutoringClass, to: LessonStatus): TutoringClass {
        require(canTransition(c.status, to)) { "Illegal transition ${c.status} -> $to" }
        val attended = when {
            to.isBillable && c.attended == Attended.EXPECTED -> Attended.PRESENT
            !to.isBillable && c.attended == Attended.PRESENT -> Attended.EXPECTED
            else -> c.attended
        }
        return rebill(c.copy(status = to, attended = attended))
    }

    /**
     * Records whether one student was in a lesson, and rebills them for it.
     *
     * This is the per-member correction — the absentee of a lesson that went ahead — and it is a change
     * to *their* row alone. It used to be expressible only as a status change to a session of one, which
     * left the lesson itself holding several statuses and no way to say "absent, and charged anyway".
     */
    fun applyAttendance(c: TutoringClass, attended: Attended): TutoringClass =
        rebill(c.copy(attended = attended))

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
     *   [LessonStatus.SCHEDULED], any captured [TutoringClass.charge] is voided, and anybody recorded as
     *   having been **present** goes back to merely expected — a lesson that has not happened yet had
     *   nobody in it. This lets a completed/cancelled lesson be put back on the schedule by moving it
     *   forward. An absence stands: it was somebody saying they would not be there.
     * - Moving it into the **past** keeps the status (and charge) untouched — e.g. correcting the
     *   recorded time of a lesson that already happened must not silently change whether it billed.
     */
    fun move(c: TutoringClass, newStart: Instant, now: Instant): TutoringClass =
        if (newStart.isAfter(now)) {
            c.copy(
                start = newStart,
                status = LessonStatus.SCHEDULED,
                attended = if (c.attended == Attended.PRESENT) Attended.EXPECTED else c.attended,
                charge = Money.ZERO,
            )
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
