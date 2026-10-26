package com.seblful.klas.domain.model

import kotlin.time.Instant

/**
 * One lesson and the students in it: one class for an individual lesson, one per member for a group's.
 *
 * The lesson is a **row** (`lessons`), so a session is that row's classes and nothing else has to be
 * inferred — [status] is read, not derived. Classes sharing a `(groupId, start)` pair would be an
 * invariant no schema can hold: moving one member's class on its own would leave the session by
 * definition, and one lesson would quietly become two competing for the same hour.
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
     * Here rather than derived at each call site: a group lesson has to exclude *every* member's class,
     * not just the one the tutor tapped, and "every member" is a fact about the session. A hand-derived
     * copy that misses one ships as a double booking.
     */
    val ids: Set<Long> get() = classes.mapTo(mutableSetOf()) { it.id }

    val size: Int get() = classes.size

    /**
     * **The lesson's own status** — the hour's, held once in its own row and shared by every class of it.
     *
     * Who missed the lesson is [TutoringClass.attended]'s to say, not a status, so this is simply read.
     */
    val status: LessonStatus get() = first.status

    /** How many of the lesson's students were actually in it — what "N of M" counts. */
    val present: Int get() = classes.count { it.attended.wasThere }

    /**
     * How this lesson's roster reads — see [Attendance].
     *
     * The rule is that «N из M» is only ever said about a lesson that **happened**: before then nobody has
     * missed anything and everybody is merely expected, so a count of who "came" would just be the roster
     * with a smaller-looking number next to it. The combining of `present` and `size` lives here rather
     * than in a composable, for the reason `DeleteWarning` does: it is the part that can be wrong, and a
     * composable cannot be tested.
     */
    val attendance: Attendance
        get() = if (status.isBillable && present < size) Attendance.Some(present, size) else Attendance.All(size)

    /**
     * The rate every class in the session was booked at, or null when they differ. One lesson has one
     * rate (a group's own, or the student's), so this is normally just "the lesson's rate" — it can only
     * come back null for a lesson booked before groups had a rate of their own, whose members were
     * billed individually. A form showing one price field then has nothing truthful to
     * put in it and falls back to the group's current rate.
     */
    val sharedPrice: Money? get() = classes.map { it.price }.distinct().singleOrNull()

    companion object {
        /**
         * Wraps [klass] in a session of one, preserving its group id.
         *
         * An individual lesson is complete in that one class. For a group's member the wrapper may be a
         * partial session: it does not load the lesson's other members. Whole-lesson writes need
         * the complete session read from the repository.
         *
         * Per-member attendance corrections use `ClassRepository.setAttendance`.
         */
        fun of(klass: TutoringClass): ClassSession = ClassSession(klass.groupId, listOf(klass))
    }
}

/**
 * What a lesson's roster comes to, as the **case** rather than the two numbers it is picked from — see
 * [ClassSession.attendance].
 *
 * Two cases and not "present out of members", because the two are said differently and one of them is a
 * plural: a full lesson names its size once («3 ученика»), a lesson somebody missed names both («2 из 3»).
 * A screen holding the two raw counts has to re-derive which sentence applies, and both group cards would
 * have to agree about it independently.
 */
sealed interface Attendance {

    /** Everybody the lesson is for, [count] of them — either it has not happened yet, or nobody missed it. */
    data class All(val count: Int) : Attendance

    /** Somebody was marked absent from a lesson that happened: [present] of [of] were there. */
    data class Some(val present: Int, val of: Int) : Attendance
}
