package com.seblful.klas.domain

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

    /** The group's name for a group lesson, the student's for an individual one. */
    fun titleOf(session: ClassSession): String =
        if (session.isGroup) groups[session.groupId].orEmpty() else students[session.first.studentId].orEmpty()

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
