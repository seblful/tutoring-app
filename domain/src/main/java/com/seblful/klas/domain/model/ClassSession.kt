package com.seblful.klas.domain.model

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
     * How this lesson's roster reads — see [Attendance].
     *
     * The rule is that «N из M» is only ever said about a lesson that **happened**: before then nobody has
     * missed anything and everybody is merely expected, so a count of who "came" would just be the roster
     * with a smaller-looking number next to it. It was two facts (`present`, `size`) published side by side
     * and combined by an `if` inside a `LazyColumn` item lambda, which is the shape `DeleteWarning` exists
     * to replace: the combining is the part that can be wrong, and there it had no test.
     */
    val attendance: Attendance
        get() = if (status.isBillable && present < size) Attendance.Some(present, size) else Attendance.All(size)

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
