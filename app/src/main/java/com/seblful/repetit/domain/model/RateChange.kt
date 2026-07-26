package com.seblful.repetit.domain.model

import java.time.Instant

/**
 * Whose hourly rate a [RateChange] is about.
 *
 * Exactly two things in the app carry a rate — a student's price for one subject, and a group's own
 * rate — and a change belongs to one of them, never both and never neither. Saying that as a sealed
 * type makes the impossible combinations unrepresentable in the domain: the flat nullable columns
 * that actually store it live only on `RateChangeEntity`, and `Mappers.kt` is the single place they
 * are read back into this shape.
 */
sealed interface RateOwner {

    /** One student's rate for one subject — what an individual lesson is priced from (`prices`). */
    data class StudentSubject(val studentId: Long, val subjectId: Long) : RateOwner

    /**
     * A group's own hourly rate — what every member of one of its lessons is billed
     * (`student_groups.priceMinor`).
     */
    data class GroupRate(val groupId: Long) : RateOwner
}

/**
 * One recorded change of an hourly rate: what it was, what it became, and when.
 *
 * The log exists because a rate is **current state** while a lesson bills the rate it was *booked*
 * at (see [TutoringClass.price]). That split is deliberate and is what makes re-pricing safe — but
 * it also means raising a price leaves no trace: the new number replaces the old one in `prices`,
 * the already-booked lessons keep theirs, and nothing anywhere says the two used to be equal. This
 * is the record of that, so "what did I charge them last term" is answerable.
 *
 * @param from the rate before the change, or **null when this row is where the rate started** —
 *   which is a genuinely different statement ("цена была назначена") from a change, and reads that
 *   way in the UI.
 * @param at when the change was recorded, or **null for a rate that already existed before the app
 *   kept this log** (the rows the v6 → v7 migration seeds). Its amount is known; the day it was set
 *   is not, and stamping the upgrade date on it would assert something untrue — the same reasoning
 *   that leaves a pre-v6 group with no subject rather than a guessed one. Sorting newest-first puts
 *   these last, which is where an "it was already this much" row belongs.
 */
data class RateChange(
    val id: Long = 0,
    val owner: RateOwner,
    val from: Money?,
    val to: Money,
    val at: Instant?,
) {
    /** Whether this row is where the rate's history starts, rather than a change to it. */
    val isInitial: Boolean get() = from == null
}
