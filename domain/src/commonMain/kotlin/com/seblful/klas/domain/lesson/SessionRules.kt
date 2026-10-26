package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.time.Instant

/**
 * Rules for every lesson, group or individual: how its classes collapse into one lesson, what one edit
 * does to each of them, and which statuses it may move to.
 *
 * An individual lesson is a session of one and goes through exactly the same rules as a group's.
 */
object SessionRules {

    private fun editFor(c: TutoringClass, changes: SessionEdit): ClassEdit = ClassEdit(
        studentId = changes.studentId ?: c.studentId,
        subjectId = changes.subjectId,
        durationMinutes = changes.durationMinutes,
        price = changes.price,
        mode = changes.mode,
    )

    /**
     * Every class of [session] paired with the edit [changes] implies for it — the whole fan-out of
     * one lesson-wide edit, in one place, so a caller cannot apply it to some classes and not others.
     *
     * Refuses to re-attribute a lesson of more than one class: swapping a group lesson's student
     * would silently move one member's history to somebody who was never in the room, and the roster
     * is where membership is changed.
     */
    fun editsFor(session: ClassSession, changes: SessionEdit): List<Pair<TutoringClass, ClassEdit>> {
        require(changes.studentId == null || session.size == 1) {
            "Only an individual class can be re-attributed to another student (this lesson has ${session.size})"
        }
        return session.classes.map { it to editFor(it, changes) }
    }

    /**
     * The whole of what one edit does to one lesson: its own row's new fields, and what becomes of every
     * member — [GroupRules.rosterChange], [editsFor] and [ClassRules.edit] composed in the one order they compose in.
     *
     * **The composition is this module's, not its caller's.** The assembly is where the rule can go wrong:
     * the lesson's own fields must not be read out of the surviving classes, since a total roster swap
     * leaves no class to read them from (see [LessonEdit]). Naming the sequence here also makes it
     * testable — the parts each have tests, and the thing between them needs one.
     *
     * [roster] says who the lesson covers afterwards; null leaves it exactly as it is, which is all an
     * individual lesson can mean. [now] is the caller's clock, as everywhere in the domain: whether the
     * move re-plans the lesson is decided against it.
     */
    fun lessonEdit(
        session: ClassSession,
        changes: SessionEdit,
        newStart: Instant,
        now: Instant,
        roster: Set<Long>? = null,
    ): LessonEdit {
        val change = roster?.let { GroupRules.rosterChange(session, it, changes, newStart) } ?: RosterChange()
        val leaving = change.dropped.mapTo(mutableSetOf()) { it.id }
        // editsFor runs over the whole session, so its refusal to re-attribute a group lesson is decided
        // by what the lesson *is* rather than by whoever happens to be staying in it.
        val resolved = editsFor(session, changes).map { (c, edit) -> c.id to ClassRules.edit(c, edit, newStart, now) }
        return LessonEdit(
            // The representative class, resolved, whether or not it is staying: the lesson's row holds
            // only fields every class of it shares, so any of them states them correctly.
            lesson = resolved.first().second,
            updated = resolved.filterNot { (id, _) -> id in leaving }.map { (_, c) -> c },
            joining = change.joining.map { ClassRules.move(it, newStart, now) },
            dropped = change.dropped,
        )
    }

    /**
     * Collapses [classes] into the lessons they belong to — **by `lessonId`**, a column, which is what
     * makes them one lesson. Sessions come out ordered by start, then by the id of their first class, so
     * the schedule's order is stable across re-emissions.
     *
     * Grouping by *time* instead would let a class whose time is edited on its own leave its session:
     * two rows of one lesson could disagree about when it is, and then they would be two lessons, each
     * holding the same hour.
     */
    fun sessions(classes: List<TutoringClass>): List<ClassSession> =
        classes
            .groupBy { it.lessonId }
            .map { (_, group) ->
                val sorted = group.sortedBy { it.id }
                ClassSession(groupId = sorted.first().groupId, classes = sorted)
            }
            .sortedWith(compareBy({ it.start }, { it.first.id }))

    /**
     * Every class of every lesson [studentId] is in — **whole lessons, not only their own class**.
     *
     * This is the schedule's per-student narrowing, and the distinction is the whole of it. Keeping only
     * the student's own row would leave a group lesson as a session of one: it would draw as a group
     * lesson with a roster of one, its day would total one member's money, and — the part that matters —
     * the sheet's «Проведено» would act on a session the lesson does not consist of. A filter is a way of
     * *looking*, so it narrows which lessons are shown and never what one of them is.
     *
     * Filtered here rather than in SQL because the schedule already reads a date range whole
     * ([sessions] collapses it), and a second query keyed on a student would answer a different question
     * — "their classes" — which is the answer this deliberately does not want.
     */
    fun lessonsWith(classes: List<TutoringClass>, studentId: Long): List<TutoringClass> {
        val theirLessons = classes.filter { it.studentId == studentId }
            .mapTo(mutableSetOf()) { it.lessonId }
        return classes.filter { it.lessonId in theirLessons }
    }

    /**
     * The statuses offered for a whole lesson — simply what its own status may become, since its members
     * cannot hold different statuses.
     */
    fun sessionTransitions(session: ClassSession): Set<LessonStatus> =
        ClassRules.availableTransitions(session.status)
}
