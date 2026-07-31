package com.seblful.klas.domain

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * One lesson and the students in it: one class for an individual lesson, one per member for a group's.
 *
 * The lesson is a **row** now (`lessons`), so a session is that row's classes and nothing else has to be
 * inferred — [status] is read, not derived. It used to be the classes sharing a `(groupId, start)` pair,
 * an invariant no schema could hold: moving one member's class on its own left the session by definition,
 * and one lesson quietly became two competing for the same hour.
 *
 * [classes] is never empty and is ordered by class id, so [first] is stable.
 */
data class ClassSession(
    val groupId: Long?,
    val classes: List<TutoringClass>,
) {
    init {
        require(classes.isNotEmpty()) { "A session needs at least one class" }
    }

    val isGroup: Boolean get() = groupId != null

    /** The representative class — carries the shared time, subject, mode and status. */
    val first: TutoringClass get() = classes.first()

    /** The lesson row these classes belong to. */
    val lessonId: Long get() = first.lessonId

    val start: Instant get() = first.start

    val durationMinutes: Int get() = first.durationMinutes

    /**
     * Every class id of the lesson — what an overlap check has to skip so a lesson cannot be found
     * standing in its own way (`Scheduling.conflicts`, and every write that re-occupies time).
     *
     * Here rather than at each call site because it was derived by hand at four of them, each also
     * choosing its own start-and-length pair to ask about. Three of those hand copies shipped as a
     * double booking, and the fourth way in shipped as another: a group lesson has to exclude *every*
     * member's class, not just the one the tutor tapped, and "every member" is a fact about the session.
     */
    val ids: Set<Long> get() = classes.mapTo(mutableSetOf()) { it.id }

    val size: Int get() = classes.size

    /**
     * **The lesson's own status** — the hour's, held once in its own row and shared by every class of it.
     *
     * It was a derivation twice over before. First an enum on each student's row answered both "did this
     * hour happen" and "was this student in it", so a group lesson whose absentee had been cancelled had
     * *no* status and was badged «Разные» — a fact about the roster, in a word no class can be set to.
     * Then it was that badge's replacement: the strictest status any member was in. Now who missed the
     * lesson is [TutoringClass.attended]'s to say, and this is simply read.
     */
    val status: LessonStatus get() = first.status

    /** How many of the lesson's students were actually in it — what "N of M" counts. */
    val present: Int get() = classes.count { it.attended.wasThere }

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
         * Just [klass], as the lesson it is the only member of — an individual lesson, which is what
         * every write takes.
         *
         * It used to double as the operand for a **per-member correction** inside a group lesson (cancel
         * the absentee, leave the lesson completed), which is why it drops the group id: that was one
         * person's class being changed while pretending to be a lesson. Attendance is that correction
         * now (`ClassRepository.setAttendance`), so this is only ever a lesson of one.
         */
        fun of(klass: TutoringClass): ClassSession = ClassSession(klass.groupId, listOf(klass))
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
     * group lesson can mean — who was in one is changed by adding and removing classes
     * ([GroupRules.rosterChange]), never by relabelling one of them. [GroupRules.editsFor] refuses a
     * non-null value for a lesson of more than one class rather than trusting callers to know that.
     */
    val studentId: Long? = null,
) {
    init {
        // The same rule `Scheduling.Slot` states, at the other end of the write: this is what a lesson
        // is re-billed from, so a non-positive length would also charge nothing for it.
        require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
    }
}

/**
 * What a lesson's roster change comes to: [dropped] are the classes of the members leaving it,
 * [joining] whole new classes for the ones arriving. A joiner is built unbilled — writing it is what
 * settles the charge (see [ClassRules.rebill]).
 */
data class RosterChange(
    val dropped: List<TutoringClass> = emptyList(),
    val joining: List<TutoringClass> = emptyList(),
) {
    /** The lesson keeps exactly the members it had. */
    val isEmpty: Boolean get() = dropped.isEmpty() && joining.isEmpty()

    /**
     * The leavers whose class had already billed. Removing those takes income the student was
     * charged with them, so it is worth asking about first — the same warning deleting a completed
     * lesson owes.
     */
    val billedDropped: List<TutoringClass> get() = Billing.billable(dropped)
}

/**
 * One lesson edit, resolved whole: [lesson] carries the **lesson row's** own fields afterwards — its
 * hour, length, subject, mode and status — and the three lists are what becomes of the people in it.
 * [updated] stay and are rewritten in place, [joining] arrive with no row yet, [dropped] leave.
 *
 * **[lesson] is its own field rather than "the first of [updated]", and that is the whole point of the
 * type.** A roster can be swapped *whole* — every member unticked, different ones ticked — and then
 * there is no updated class left to read the lesson's new hour off. The write that projected the lesson
 * row out of that list therefore stored the roster and silently kept the **old time**, while the hour
 * check had already reserved the new one and the calendar had already mirrored it there; the joiners
 * were re-billed from the new length against the stored old one, so a day's takings and the report
 * disagreed. Any class of the lesson is a truthful carrier of these fields (they are all shared, and
 * [ClassRules.edit] derives the status from the move alone), so there is always one to name — which is
 * exactly why leaving it implied was possible.
 */
data class LessonEdit(
    val lesson: TutoringClass,
    val updated: List<TutoringClass>,
    val joining: List<TutoringClass> = emptyList(),
    val dropped: List<TutoringClass> = emptyList(),
)

/** Rules for groups: who a group's lesson covers, and how its classes read as one lesson. */
object GroupRules {

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
     * How [session]'s roster has to change for the lesson to cover exactly [wanted].
     *
     * Who was in a lesson is a fact the tutor may correct afterwards — somebody turned out not to
     * have come, somebody came who was never ticked — so this is expressible for a lesson in any
     * status, past or future. What it must not do is invent history: a joiner joins **the lesson**, so
     * they get its id and its status, and their attendance follows from it — somebody added to a lesson
     * that was taught was in it, and owes exactly what being in it owed, while somebody added to one
     * still to come is merely expected.
     *
     * A leaver's row is **deleted, not marked absent**: absent records somebody who missed a lesson they
     * were in, which is a different fact from their never having been in it.
     */
    fun rosterChange(
        session: ClassSession,
        wanted: Set<Long>,
        changes: SessionEdit,
        newStart: Instant,
    ): RosterChange {
        require(session.isGroup) { "Only a group lesson has a roster to change" }
        require(wanted.isNotEmpty()) { "A lesson needs at least one student" }
        val booked = session.classes.map { it.studentId }.toSet()
        return RosterChange(
            dropped = session.classes.filterNot { it.studentId in wanted },
            joining = (wanted - booked).map { studentId ->
                TutoringClass(
                    lessonId = session.lessonId,
                    studentId = studentId,
                    subjectId = changes.subjectId,
                    start = newStart,
                    durationMinutes = changes.durationMinutes,
                    price = changes.price,
                    status = session.status,
                    attended = if (session.status.isBillable) Attended.PRESENT else Attended.EXPECTED,
                    groupId = session.groupId,
                    mode = changes.mode,
                )
            },
        )
    }

    /**
     * The whole of what one edit does to one lesson: its own row's new fields, and what becomes of every
     * member — [rosterChange], [editsFor] and [ClassRules.edit] composed in the one order they compose in.
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
        val change = roster?.let { rosterChange(session, it, changes, newStart) } ?: RosterChange()
        val leaving = change.dropped.mapTo(mutableSetOf()) { it.id }
        // editsFor runs over the whole session, so its refusal to re-attribute a group lesson is decided
        // by what the lesson *is* rather than by whoever happens to be staying in it.
        val resolved = editsFor(session, changes).map { (c, edit) -> c.id to ClassRules.edit(c, edit, newStart, now) }
        return LessonEdit(
            // The representative class, resolved, whether or not it is staying: the lesson's row holds
            // only fields every class of it shares, so any of them states them correctly.
            lesson = resolved.first().second,
            updated = resolved.filterNot { (id, _) -> id in leaving }.map { (_, c) -> c },
            joining = change.joining,
            dropped = change.dropped,
        )
    }

    /**
     * Who a lesson may be offered to: the group's **bookable** members (archived students left out),
     * plus anybody the lesson [alreadyIn] — a member archived, or dropped from the group, since it was
     * booked is still in that lesson, so they must still be listed and still be untickable.
     *
     * Filtered out of [everyone] rather than concatenated, so the union keeps the caller's name ordering
     * instead of listing the strays at the end.
     *
     * This set algebra was spelled five times inside one view model — twice as a flow, twice as a
     * suspend read, once as its complement — while every other roster rule lived here. Two of the copies
     * had already drifted: one applied [bookableMembers] to a list that was already active-only, so the
     * archiving half of the rule was really being applied at exactly one of them.
     */
    fun offeredRoster(
        everyone: List<Student>,
        memberIds: Set<Long>,
        alreadyIn: Set<Long> = emptySet(),
    ): List<Student> {
        val offered = bookableMembers(everyone.filter { it.id in memberIds })
            .mapTo(mutableSetOf()) { it.id } + alreadyIn
        return everyone.filter { it.id in offered }
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
     * The members a lesson can actually be booked for: archived students are left out. Archiving is
     * how a student leaves without erasing history, so a group they were in must stop billing them —
     * but their past group lessons stay untouched.
     */
    fun bookableMembers(members: List<Student>): List<Student> = members.filterNot { it.archived }

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
