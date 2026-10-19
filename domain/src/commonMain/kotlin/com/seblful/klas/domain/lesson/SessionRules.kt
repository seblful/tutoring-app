package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.time.Instant

/**
 * Rules for every lesson, group or individual: how its classes collapse into one lesson, what one edit
 * does to each of them, and which statuses it may move to.
 *
 * These lived in [GroupRules] because group lessons were where a lesson first had several classes; an
 * individual lesson is a session of one and goes through exactly the same rules.
 */
object SessionRules {

    /**
     * The per-class edit that [changes] implies for [c]: the lesson's shared fields as given — its
     * one [SessionEdit.price] included — and the class's own student, or [SessionEdit.studentId] when
     * an individual class is being re-attributed to somebody else.
     *
     * Private: it is a step of [editsFor], and calling it directly walks past the one thing [editsFor]
     * refuses — re-attributing a lesson of more than one class. It was public with no caller but its
     * own test, which is a hole in this module's guarantee kept open by nothing.
     */
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
     * **The composition is this module's, not its caller's.** These three used to be assembled at the one
     * call site that needs them, and the assembly is where the rule went wrong: the lesson's own fields
     * were read out of the surviving classes, so a total roster swap left no class to read them from. See
     * [LessonEdit] for what that cost. Naming the sequence here is also what makes it testable at all —
     * the parts each had tests, and the thing between them had none.
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
     * Collapses [classes] into the lessons they belong to — **by `lessonId`**, which is what makes them
     * one lesson and is now a column rather than a coincidence. Sessions come out ordered by start, then
     * by the id of their first class, so the schedule's order is stable across re-emissions.
     *
     * This used to group by `(groupId, start)` and fall back to the class's own id for an individual one.
     * The key being the *time* meant a class whose time was edited on its own left its session by
     * construction — described at the time as there being "no membership flag to go stale", which is true
     * and was the problem: two rows of one lesson could disagree about when it was, and then they were
     * two lessons, each holding the same hour.
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
     * The statuses offered for a whole lesson — simply what its own status may become.
     *
     * It used to be the *union* over its classes, with an `applicableTo` beside it picking out the ones
     * the change was legal for and leaving the rest alone: machinery for a lesson whose members held
     * different statuses, which cannot happen now that the status is the lesson's. Both went with it.
     */
    fun sessionTransitions(session: ClassSession): Set<LessonStatus> =
        ClassRules.availableTransitions(session.status)
}
