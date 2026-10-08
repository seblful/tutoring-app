package com.seblful.klas.domain

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass

/**
 * The names a lesson is captioned with, looked up once.
 *
 * A lesson is stored as ids — a student, a subject, sometimes a group — and every screen that lists one
 * has to turn those back into words. Three view models did it for themselves, in three shapes: two built
 * `associateBy` maps and one ran a linear `firstOrNull { it.id == … }` **per emission**. The rule that goes
 * with it is small but real and was written out each time: **a group lesson is titled by its group and an
 * individual one by its student**, which is the difference between «11 «А», 18:00» and «Иван, 18:00» on
 * the busiest screen in the app.
 *
 * Built once per emission from whole-table lists and then asked per lesson, which is what the maps were
 * for; the construction is [of].
 *
 * A name this set has never heard of comes back **blank rather than absent**, because every caller is
 * drawing a row and a row with a hole in it is worse than one with a gap: an archived student, a subject
 * deleted between two emissions, a group id from a lesson the query outran. The one exception is
 * [groupOf], where "no group" is a real and different answer from "a group whose name I do not know".
 */
class Captions private constructor(
    private val students: Map<Long, String>,
    private val subjects: Map<Long, String>,
    private val groups: Map<Long, String>,
) {

    /**
     * The group's name for a group lesson, the student's for an individual one — by [title], so a group
     * lesson whose group this set has never heard of is titled by its first student rather than by nothing.
     */
    fun titleOf(session: ClassSession): String =
        title(groupName = session.groupId?.let { groups[it] }, studentName = students[session.first.studentId])

    /** What the lesson is in. Every class of one lesson shares a subject, so the first speaks for all. */
    fun subjectOf(session: ClassSession): String = subjects[session.first.subjectId].orEmpty()

    /**
     * A group lesson's students, in class order; **empty for an individual lesson**, whose one student is
     * already the title.
     */
    fun membersOf(session: ClassSession): List<String> =
        if (session.isGroup) session.classes.map { students[it.studentId].orEmpty() } else emptyList()

    /** What one class is in — for a list that mixes lessons rather than collapsing them into sessions. */
    fun subjectOf(klass: TutoringClass): String = subjects[klass.subjectId].orEmpty()

    /**
     * Who a piece of homework is for, by the same rule a lesson is titled by: the group's name for a
     * group's, the student's for one student's.
     */
    fun titleOf(assignee: Assignee): String = when (assignee) {
        is Assignee.Student -> students[assignee.studentId].orEmpty()
        is Assignee.Group -> groups[assignee.groupId].orEmpty()
    }

    /**
     * The group [klass] was taught in, or **null when it was an individual lesson**.
     *
     * Null is the meaningful answer here, not a missing name: a student's own lesson list mixes both
     * kinds, and «Биология, 18:00» twice over — once billed at 30 and once at 20 — is a puzzle until one
     * of them says which was the group's.
     *
     * A group whose name is unknown reads as **no group**, which is the one place this class prefers null
     * to a blank: the caller draws a label only when there is one, and an empty label is a visible piece of
     * furniture saying nothing, where the absence is merely the individual-lesson layout.
     */
    fun groupOf(klass: TutoringClass): String? = klass.groupId?.let { groups[it] }

    companion object {
        /**
         * **The** naming rule for a lesson: [groupName] when it belongs to a group whose name is known and
         * not blank, otherwise [studentName]; blank when neither is known.
         *
         * Over names rather than ids, because two of its readers hold no [Captions]: a reminder names its
         * lesson from rows read as it fires (`Reminders.announcedBy`), and the booking form names the lesson
         * standing in its way. Each spelled the rule for itself, and they disagreed about a group with no
         * known name — the schedule titled it with nothing where the reminder named a student. A name says
         * something about who is coming; a blank says nothing.
         */
        fun title(groupName: String?, studentName: String?): String =
            groupName?.takeIf { it.isNotBlank() } ?: studentName.orEmpty()

        fun of(
            students: List<Student>,
            subjects: List<Subject>,
            groups: List<Group>,
        ): Captions = Captions(
            students = students.associate { it.id to it.name },
            subjects = subjects.associate { it.id to it.name },
            groups = groups.associate { it.id to it.name },
        )
    }
}
