package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.TutoringClass
import java.time.Instant

/**
 * One lesson as the tutor thinks of it: either a single student's class, or the several classes of
 * one group lesson (same group, same start).
 *
 * A session is **derived, never stored** — the group id and start on each class are the whole truth,
 * like balances being derived from classes and payments. [classes] is never empty and is ordered by
 * class id, so [first] is stable.
 */
data class ClassSession(
    val groupId: Long?,
    val classes: List<TutoringClass>,
) {
    init {
        require(classes.isNotEmpty()) { "A session needs at least one class" }
    }

    val isGroup: Boolean get() = groupId != null

    /** The representative class — carries the shared time, subject and mode. */
    val first: TutoringClass get() = classes.first()

    val start: Instant get() = first.start

    val size: Int get() = classes.size

    /**
     * The status every class in the session shares, or null when they differ — which happens on
     * purpose: one absentee's class is cancelled while the rest stay completed.
     */
    val commonStatus: ClassStatus? get() = classes.map { it.status }.distinct().singleOrNull()

    /**
     * The rate every class in the session was booked at, or null when they differ. One lesson now
     * has one rate (a group's own, or the student's), so this is normally just "the lesson's rate" —
     * it can only come back null for a lesson booked before groups had a rate of their own, whose
     * members were billed individually. A form showing one price field then has nothing truthful to
     * put in it and falls back to the group's current rate.
     */
    val sharedPrice: Money? get() = classes.map { it.price }.distinct().singleOrNull()

    companion object {
        /**
         * Just [klass], as a session of one — the operand for changing a single class, including a
         * **per-member correction** inside a group lesson (cancel the absentee, leave the lesson
         * completed). Carries no group id: this is one person's class being changed, not the lesson.
         */
        fun of(klass: TutoringClass): ClassSession = ClassSession(null, listOf(klass))
    }
}

/**
 * The user-editable fields of a whole lesson — one student's or a group's. Every field is shared by
 * every class in it, [price] included: a group lesson is billed at the **group's** one hourly rate,
 * and an individual lesson is a lesson of one, so there is a single rate either way.
 */
data class SessionEdit(
    val subjectId: Long,
    val durationMinutes: Int,
    val mode: ClassMode,
    /** The hourly rate every class of the lesson is (re)billed at. */
    val price: Money,
    /**
     * Who the lesson belongs to after the edit. An **individual** class may be re-attributed to
     * another student, which credits the new owner at [price].
     *
     * Null means "leave every class with the student it already has", which is the only thing a
     * group lesson can mean — its members come from the group's roster, so they are changed by
     * editing the roster, never by editing one of its lessons. [GroupRules.editsFor] refuses a
     * non-null value for a lesson of more than one class rather than trusting callers to know that.
     */
    val studentId: Long? = null,
)

/** Rules for groups: who a group's lesson covers, and how its classes read as one lesson. */
object GroupRules {

    /**
     * The per-class edit that [changes] implies for [c]: the lesson's shared fields as given — its
     * one [SessionEdit.price] included — and the class's own student, or [SessionEdit.studentId] when
     * an individual class is being re-attributed to somebody else.
     */
    fun editFor(c: TutoringClass, changes: SessionEdit): ClassEdit = ClassEdit(
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
     * Collapses [classes] into sessions: classes sharing a (group, start) pair become one session,
     * every other class stands alone. Sessions come out ordered by start, then by the id of their
     * first class, so the schedule's order is stable across re-emissions.
     *
     * A class whose time is edited on its own therefore leaves its session by construction — there
     * is no membership flag to go stale, because the session key *is* the time it happens at.
     */
    fun sessions(classes: List<TutoringClass>): List<ClassSession> =
        classes
            // Individual classes get a per-class key so they can never merge with each other.
            .groupBy { c -> c.groupId?.let { it to c.start.toEpochMilli() } ?: (null to c.id) }
            .map { (key, group) ->
                ClassSession(groupId = key.first, classes = group.sortedBy { it.id })
            }
            .sortedWith(compareBy({ it.start }, { it.first.id }))

    /**
     * The members a lesson can actually be booked for: archived students are left out. Archiving is
     * how a student leaves without erasing history, so a group they were in must stop billing them —
     * but their past group lessons stay untouched.
     */
    fun bookableMembers(members: List<Student>): List<Student> = members.filterNot { it.archived }

    /**
     * The statuses offered for a whole session: the union of what its classes can move to. A mixed
     * session (say two completed, one cancelled) can still be sent to either status — [applicableTo]
     * then picks out the classes the change is legal for, leaving the rest alone.
     */
    fun sessionTransitions(session: ClassSession): Set<ClassStatus> =
        session.classes.flatMap { ClassRules.availableTransitions(it.status) }.toSet()

    /**
     * The classes in [session] that may legally move to [to] — the others are already there, or the
     * transition isn't allowed for them. Applying a session-wide status change to exactly these
     * keeps a partly-corrected group lesson from throwing.
     */
    fun applicableTo(session: ClassSession, to: ClassStatus): List<TutoringClass> =
        session.classes.filter { it.status != to && ClassRules.canTransition(it.status, to) }
}
