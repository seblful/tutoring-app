package com.seblful.klas.domain.reports

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass

/**
 * How much teaching a card is summing up: how many lessons happened, how long they ran, what they
 * came to, and how many are still ahead.
 *
 * This is the arithmetic behind the stat row at the top of a student's and a group's card. It is
 * here rather than in either view model because the two count *differently* and that difference is
 * a rule, not a layout detail — see [byGroup]. Money owed is deliberately **not** part of it: a
 * balance is `Billing`'s job, and only a student has one.
 */
object Taught {

    /**
     * @param lessons lessons that happened (billable).
     * @param upcoming lessons still scheduled.
     * @param cancelled lessons that were called off.
     * @param minutes how long the [lessons] ran in total — the tutor's time, not the students' —
     *   so a 90-minute group lesson counts 90 minutes however many people were in the room.
     * @param earned recognized income: the charges captured on completed classes, which *is* summed
     *   per student, because that is who owes it.
     */
    data class Totals(
        val lessons: Int,
        val upcoming: Int,
        val cancelled: Int,
        val minutes: Int,
        val earned: Money,
    ) {
        /** Two disjoint sets of teaching added up — see [overall], the only thing that needs it. */
        operator fun plus(other: Totals) = Totals(
            lessons = lessons + other.lessons,
            upcoming = upcoming + other.upcoming,
            cancelled = cancelled + other.cancelled,
            minutes = minutes + other.minutes,
            earned = earned + other.earned,
        )

        /**
         * What the «Занятий» tile says under its headline number, or null when there is nothing to add.
         *
         * **Lessons still to come beat lessons that fell through**, and neither is said when it is zero:
         * «впереди 3» is something to act on, «отменено 3» is not, and only one line fits under the count.
         * The precedence lives here rather than in a `@Composable`, so the one rule the student and the
         * group card genuinely agree on can be tested.
         *
         * A card is free to put something else in the slot when this is null; the student's puts the hours
         * there and the group's deliberately does not. That choice is the card's, not this rule's.
         */
        val hint: Hint?
            get() = when {
                upcoming > 0 -> Hint.Upcoming(upcoming)
                cancelled > 0 -> Hint.Cancelled(cancelled)
                else -> null
            }

        companion object {
            val NONE = Totals(lessons = 0, upcoming = 0, cancelled = 0, minutes = 0, earned = Money.ZERO)
        }
    }

    /** What [Totals.hint] came to — the case, leaving the wording to whichever card is drawing it. */
    sealed interface Hint {
        /** [count] lessons are still scheduled. */
        data class Upcoming(val count: Int) : Hint

        /** [count] lessons were called off, and none is still to come. */
        data class Cancelled(val count: Int) : Hint
    }

    /** A student's card: every one of their classes is one lesson of theirs. */
    fun byStudent(classes: List<TutoringClass>): Totals {
        val completed = Billing.billable(classes)
        return Totals(
            lessons = completed.size,
            upcoming = classes.count { it.status == LessonStatus.SCHEDULED },
            cancelled = classes.count { it.status == LessonStatus.CANCELLED },
            minutes = completed.sumOf { it.durationMinutes },
            earned = Billing.charged(classes),
        )
    }

    /**
     * Everything the tutor taught, both kinds at once — what the reports screen leads with.
     *
     * Individual teaching is counted per student's class; group teaching is counted per session,
     * so a three-member group lesson contributes one lesson and its own duration, while income
     * still sums each member's charge. Each half uses the same rule as its detail card, and the
     * two totals are added here. [SessionRules.sessions] groups by lesson id and keeps separate
     * individual lessons distinct.
     */
    fun overall(classes: List<TutoringClass>): Totals {
        val (group, individual) = classes.partition { it.groupId != null }
        return byStudent(individual) + byGroup(group)
    }

    /**
     * A group's card, where a lesson is a **session** rather than a class: a lesson taught to three
     * members is *one* lesson of the tutor's, and it ran for its own duration — counting the three
     * classes would report a group as three times as busy as it was, and its 90-minute evening as
     * four and a half hours.
     *
     * [Totals.earned] stays a sum over the classes, because money genuinely is per student: the
     * group decides how much, each member owes their share of it (see `Group.price`). So a group's
     * card reads "10 lessons, 15 hours, 750 BYN" — the honest shape of "what I taught, what it paid".
     *
     * Upcoming and cancelled read the lesson's own [ClassSession.status], held once for all of it. A
     * session counts as taught when **any** of its classes bills — the lesson happened and somebody was
     * in it — so an absentee marked on a completed lesson leaves it one lesson rather than a lost one.
     * Duration comes from the session's first class, since every class of one lesson shares it by
     * construction.
     */
    fun byGroup(classes: List<TutoringClass>): Totals = bySessions(SessionRules.sessions(classes))

    /**
     * [byGroup] for a caller that has already collapsed the classes into sessions.
     *
     * A separate name rather than an overload: both take a `List`, which erases to the same JVM
     * signature.
     *
     * Lets the group card, which collapses the sessions to list the lessons, avoid collapsing the same
     * list again through [byGroup] on every emission.
     */
    fun bySessions(sessions: List<ClassSession>): Totals {
        val taught = sessions.filter { s -> Billing.billable(s.classes).isNotEmpty() }
        return Totals(
            lessons = taught.size,
            upcoming = sessions.count { it.status == LessonStatus.SCHEDULED },
            cancelled = sessions.count { it.status == LessonStatus.CANCELLED },
            minutes = taught.sumOf { it.first.durationMinutes },
            earned = Billing.charged(sessions.flatMap { it.classes }),
        )
    }
}
