package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassStatus
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

        companion object {
            val NONE = Totals(lessons = 0, upcoming = 0, cancelled = 0, minutes = 0, earned = Money.ZERO)
        }
    }

    /** A student's card: every one of their classes is one lesson of theirs. */
    fun byStudent(classes: List<TutoringClass>): Totals {
        val completed = classes.filter { it.status.isBillable }
        return Totals(
            lessons = completed.size,
            upcoming = classes.count { it.status == ClassStatus.SCHEDULED },
            cancelled = classes.count { it.status == ClassStatus.CANCELLED },
            minutes = completed.sumOf { it.durationMinutes },
            earned = Billing.charged(classes),
        )
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
     * A session counts as taught when **any** of its classes is completed, which is what makes a
     * per-member correction (the lesson happened, one absentee's class cancelled) still one lesson
     * rather than a lost one; it is cancelled only when every member's class is. Duration comes from
     * the session's first class, since every class of one lesson shares it by construction.
     */
    /**
     * Everything the tutor taught, both kinds at once — what the reports screen leads with.
     *
     * Neither rule above covers a mixed set: counting every class would report a group evening as
     * three lessons and nine hours, and counting every session would collapse two students' separate
     * Tuesdays into one only because a session of one carries no group. So each half is counted by
     * its own rule and the two are added — which is the whole reason [byStudent] and [byGroup] differ
     * in the first place, applied to the one list that genuinely holds both.
     */
    fun overall(classes: List<TutoringClass>): Totals {
        val (group, individual) = classes.partition { it.groupId != null }
        return byStudent(individual) + byGroup(group)
    }

    fun byGroup(classes: List<TutoringClass>): Totals {
        val sessions = GroupRules.sessions(classes)
        val taught = sessions.filter { s -> s.classes.any { it.status.isBillable } }
        return Totals(
            lessons = taught.size,
            upcoming = sessions.count { s ->
                s.classes.none { it.status.isBillable } && s.classes.any { it.status == ClassStatus.SCHEDULED }
            },
            cancelled = sessions.count { s -> s.classes.all { it.status == ClassStatus.CANCELLED } },
            minutes = taught.sumOf { it.first.durationMinutes },
            earned = Billing.charged(classes),
        )
    }
}
