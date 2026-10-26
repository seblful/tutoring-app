package com.seblful.klas.domain.model

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
 * A named set of students taught together, at **one rate for the whole group**.
 *
 * @param price the group's **hourly rate** — what every member of a group lesson is billed, whoever
 *   they are and whatever the subject. Group teaching is priced as its own thing (usually below the
 *   one-to-one rate), so the rate belongs to the group rather than being assembled from the members'
 *   individual [Price]s. Booking a lesson still writes one [TutoringClass] per member, each carrying
 *   this rate, so balances, debts and reports stay strictly **per student** — the group decides *how
 *   much*, never *who owes it*. Editing the rate never touches lessons already booked (each class
 *   snapshots the rate it was booked at).
 *
 * A group can be renamed, re-staffed, re-priced or archived without touching history. Archived
 * groups are hidden from lists and pickers but keep their past lessons.
 */
data class Group(
    val id: Long = 0,
    val name: String,
    val note: String = "",
    val price: Money = Money.ZERO,
    /**
     * What the group is taught. Null means a group from before subjects were fixed on the group —
     * it keeps the old freedom (any member, subject chosen per lesson). Once set, the roster is
     * limited to students priced for it and its lessons are all this subject.
     */
    val subjectId: Long? = null,
    val defaultMode: ClassMode = ClassMode.IN_PERSON,
    val archived: Boolean = false,
)

/**
 * The current price for one (student, subject) pair. Editing it never alters lessons already taught:
 * a class captures the rate it was booked at (see [TutoringClass.price]).
 */
data class Price(
    val studentId: Long,
    val subjectId: Long,
    val amount: Money,
)

/**
 * One student's class: their place in one lesson, with that lesson's own facts alongside it.
 *
 * **A joined read model, not a row.** Storage keeps the lesson once (`lessons`) and the student's part in
 * it once (`attendance`); this is the two of them together, which is what the app means by "a class"
 * and what every rule below the UI reads. `ClassDao`'s queries are where the join happens, so
 * nothing above storage assembles it by hand.
 *
 * @param lessonId the lesson this belongs to. Its members are exactly the classes sharing it — see
 *   `SessionRules.sessions`.
 * @param status the **lesson's** — whether the hour happened. Shared by every class of one lesson,
 *   because it is one fact about one hour.
 * @param attended whether *this student* was in it. Independent of [status] on purpose: a lesson can be
 *   taught with somebody missing, and a missing student can still owe ([Attended.ABSENT_BILLED]).
 * @param price the **hourly rate** chosen when the lesson was booked — defaulted from the student's
 *   [Price] for the subject, or from the [Group]'s own rate for a group lesson, then editable. The
 *   [charge] is this rate scaled by the duration, so the booked rate — not a later price edit — is what
 *   gets billed.
 * @param charge recognized income: [Money.ZERO] unless [isBillable].
 * @param groupId the [Group] whose lesson this is, or null for an individual one.
 * @param mode in person or remote; pre-filled from the student's default, editable per lesson.
 * @param calendarEventId id of the mirrored Google Calendar event, if synced. Per student: a group
 *   lesson mirrors as one event each, so each keeps their own invitation.
 *
 * No text of the tutor's rides on a lesson: homework is its own thing ([Homework]), attached to a lesson
 * by rule rather than stored on one.
 */
data class TutoringClass(
    val id: Long = 0,
    val lessonId: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val start: Instant,
    val durationMinutes: Int,
    val price: Money = Money.ZERO,
    val status: LessonStatus = LessonStatus.SCHEDULED,
    val attended: Attended = Attended.EXPECTED,
    val charge: Money = Money.ZERO,
    val groupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
    val calendarEventId: String? = null,
) {
    val end: Instant get() = start.plus((durationMinutes * 60L).seconds)

    /**
     * Whether this student owes for this lesson: **the lesson happened and they were in it** — or were
     * not, under a policy that charges anyway ([Attended.ABSENT_BILLED]).
     *
     * The two halves are the whole point of the model: a no-show is recorded as absent rather than by
     * cancelling their class, and one can still be charged without being recorded as present.
     */
    val isBillable: Boolean get() = status.isBillable && attended.bills
}

/** A payment received from a student. Reduces the student's balance. */
data class Payment(
    val id: Long = 0,
    val studentId: Long,
    val date: Instant,
    val amount: Money,
    val method: PaymentMethod? = null,
)

/**
 * A student's money position: [balance] is positive when the student owes the tutor, negative when they
 * have credit from over-payment.
 */
data class StudentBalance(
    val studentId: Long,
    val charged: Money,
    val paid: Money,
) {
    val balance: Money get() = charged - paid

    /** Which of the three cases [balance] is — see [Balance], where the sign is read once. */
    val state: Balance get() = Balance.of(balance)
}
