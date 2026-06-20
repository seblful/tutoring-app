package com.seblful.repetit.domain.model

import java.time.Instant

/** A subject the tutor teaches, e.g. "Математика". */
data class Subject(
    val id: Long = 0,
    val name: String,
)

/** A student. Archived students are hidden from active lists but retain history. */
data class Student(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val defaultMode: ClassMode = ClassMode.IN_PERSON,
    val archived: Boolean = false,
)

/**
 * The current price for one (student, subject) pair. Editing it never alters classes
 * already completed: a completed class captures its charge at completion time (see
 * [TutoringClass.charge]).
 */
data class Price(
    val studentId: Long,
    val subjectId: Long,
    val amount: Money,
)

/**
 * A single class occurrence.
 *
 * @param price the **hourly rate** chosen when the class was booked (defaulted from the student's
 *   [Price] for the subject, then editable). The [charge] at completion is this rate scaled by the
 *   lesson's duration, so the booked rate — not a later price edit — is what gets billed.
 * @param charge recognized income: the hourly [price] scaled by [durationMinutes] once the class is
 *   completed (see `ClassRules.chargeFor`), [Money.ZERO] while scheduled or once cancelled.
 * @param recurringGroupId groups occurrences generated from one weekly rule; null for
 *   one-off classes.
 * @param mode in person or remote; pre-filled from the student's default, editable per class.
 * @param calendarEventId id of the mirrored Google Calendar event, if synced.
 */
data class TutoringClass(
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val start: Instant,
    val durationMinutes: Int,
    val price: Money = Money.ZERO,
    val status: ClassStatus = ClassStatus.SCHEDULED,
    val charge: Money = Money.ZERO,
    val recurringGroupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
    val calendarEventId: String? = null,
) {
    val end: Instant get() = start.plusSeconds(durationMinutes * 60L)
}

/** A payment received from a student. Reduces the student's balance. */
data class Payment(
    val id: Long = 0,
    val studentId: Long,
    val date: Instant,
    val amount: Money,
)

/**
 * A student's money position.
 *
 * @param balance positive means the student owes the tutor; negative means the student
 *   has credit from over-payment.
 */
data class StudentBalance(
    val studentId: Long,
    val charged: Money,
    val paid: Money,
) {
    val balance: Money get() = charged - paid
}
