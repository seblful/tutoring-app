package com.seblful.klas.queries.students

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.billing.PaymentSuggestions
import com.seblful.klas.domain.lesson.CardLessons
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.queries.homework.HomeworkLists
import kotlin.time.Instant

/**
 * A subject the student takes, with its per-class price and how that price got there.
 *
 * @param history every recorded change to [price], newest first. Empty means the rate has never
 * moved, which the card says out loud rather than leaving blank.
 */
data class SubjectPrice(
    val subject: Subject,
    val price: Money,
    val history: List<RateChange> = emptyList(),
)

/**
 * A class plus what it takes to read it on its own: the subject's name, and — for a group lesson —
 * the group it was one of.
 *
 * @param groupName the group whose lesson this was, or null for an individual one. It is the *name*
 * rather than a flag because "which group" is the useful half of "not individual": a student in two
 * groups is billed a different rate by each.
 */
data class ClassRow(
    val klass: TutoringClass,
    val subjectName: String,
    val groupName: String? = null,
) {
    /** Read off the class, so a group lesson whose group row can't be found still reads as one. */
    val kind: LessonKind get() = LessonKind.of(klass)

    /** The amount at the row's trailing edge, or null where the lesson billed them nothing. See [Billing.billedOrNull]. */
    val shownCharge: Money? get() = Billing.billedOrNull(listOf(klass))

    /**
     * What a planned lesson will bill at its booked rate, drawn muted where [shownCharge] has nothing — so the
     * column of amounts has no holes. Null for a taught or cancelled one.
     */
    val expectedCharge: Money?
        get() = if (klass.status ==
            LessonStatus.SCHEDULED
        ) ClassRules.chargeFor(klass.price, klass.durationMinutes) else null
}

data class StudentDetails(
    val student: Student? = null,
    /** Where the student stands — the case, not the signed amount. See [Balance]. */
    val balance: Balance = Balance.Settled,
    /** How much has been taught and earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val subjects: List<SubjectPrice> = emptyList(),
    val allSubjects: List<Subject> = emptyList(),
    val classes: List<ClassRow> = emptyList(),
    val payments: List<Payment> = emptyList(),
    /** How many lessons of each kind, for the lessons list's chips. A kind with none is absent. */
    val lessonKinds: Map<LessonKind, Int> = emptyMap(),
    /**
     * Their homework and their groups', current and done — the card's homework list, and the one place an
     * archived student's is still listed (the «Задания» tab leaves them out). See [HomeworkRules.lists].
     */
    val homework: HomeworkLists = HomeworkLists(current = emptyList(), archive = emptyList()),
) {
    /**
     * Whether narrowing the lesson list by kind means anything here. A student taught only one way
     * gets no chips: every chip but one would be empty, and «Все» would filter nothing.
     */
    val hasBothKinds: Boolean get() = lessonKinds.size > 1

    /** The subjects that can still be added: every subject not already priced for them. */
    val addableSubjects: List<Subject>
        get() {
            val priced = subjects.mapTo(mutableSetOf()) { it.subject.id }
            return allSubjects.filter { it.id !in priced }
        }

    /** The lessons list's rows under [filter], and of [status] only when one is picked. */
    fun lessonsShown(filter: LessonFilter, status: LessonStatus? = null): List<ClassRow> =
        (if (hasBothKinds) filter.accepted(classes) { it.kind } else classes)
            .filter { status == null || it.klass.status == status }

    /** The lesson the tutor next sees them at — see [CardLessons.next]. */
    fun nextLesson(now: Instant): ClassRow? = CardLessons.next(classes, now) { it.klass }

    /** The few lessons the card shows under its lessons heading — see [CardLessons.preview]. */
    fun lessonPreview(now: Instant, limit: Int): List<ClassRow> =
        CardLessons.preview(classes, now, limit) { it.klass }

    /**
     * When they last paid, or null if they never have — the line under the balance. It used to repeat the
     * balance in words («Долг 45 BYN» under «Долг / 45 BYN»); when the money last came in is the thing the
     * number does not say, and what a tutor deciding whether to chase it wants next.
     */
    val lastPaymentAt: Instant? get() = payments.maxOfOrNull { it.date }

    /** What a new payment of theirs offers at one tap: the balance, and their last billed lesson. */
    val paymentSuggestions: PaymentSuggestions
        get() = PaymentSuggestions.of(
            balance,
            classes.filter { it.shownCharge != null }.maxByOrNull { it.klass.start }?.shownCharge,
        )

    /** How they paid last — what a new payment of theirs is offered as. */
    val lastPaymentMethod: PaymentMethod? get() = payments.maxByOrNull { it.date }?.method

    /** What the archive action should do, given what this student is carrying. */
    val archiveIntent: ArchiveIntent
        get() = when {
            student == null -> ArchiveIntent.Nothing
            // Restoring costs nothing: the lessons are not coming back and the debt was never gone.
            student.archived -> ArchiveIntent.Restore
            else -> ArchiveIntent.of(balance, totals.upcoming)
        }
}

/**
 * What tapping archive/restore means here — and, when it means asking first, what the question has to
 * account for.
 */
sealed interface ArchiveIntent {

    /** No student loaded yet, so there is nothing to offer. */
    data object Nothing : ArchiveIntent

    /** Archive without asking: nothing is lost that the tutor has not already seen. */
    data object Archive : ArchiveIntent

    /** Un-archive. Never asks — restoring takes nothing away. */
    data object Restore : ArchiveIntent

    /**
     * Ask first, and say what it costs.
     *
     * @param openBalance an unsettled balance to mention before stopping weekly debt reminders;
     * null when [Balance.Settled]. Credit is also unsettled money the tutor still owes back.
     * @param upcomingLessons how many planned lessons would be called off; 0 when none would.
     */
    data class Confirm(val openBalance: Balance?, val upcomingLessons: Int) : ArchiveIntent

    companion object {
        /** [Archive] when archiving costs nothing worth naming, [Confirm] with the parts that apply. */
        fun of(balance: Balance, upcoming: Int): ArchiveIntent {
            val open = balance.takeIf { it != Balance.Settled }
            return if (open == null && upcoming <= 0) Archive
            else Confirm(openBalance = open, upcomingLessons = upcoming.coerceAtLeast(0))
        }
    }
}
