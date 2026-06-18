package com.seblful.tutoringapp.domain.model

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
    val note: String = "",
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
 * @param charge captured at completion from the then-current [Price]; [Money.ZERO]
 *   while scheduled or once cancelled/missed.
 * @param recurringGroupId groups occurrences generated from one weekly rule; null for
 *   one-off classes.
 * @param location optional place, surfaced in the reminder notification.
 * @param calendarEventId id of the mirrored Google Calendar event, if synced.
 */
data class TutoringClass(
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val start: Instant,
    val durationMinutes: Int,
    val status: ClassStatus = ClassStatus.SCHEDULED,
    val charge: Money = Money.ZERO,
    val recurringGroupId: Long? = null,
    val location: String = "",
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
