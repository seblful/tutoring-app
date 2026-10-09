package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/** Rules for a group's roster: who a group's lesson is offered to, and how its roster changes. */
object GroupRules {

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
     * The members a lesson can actually be booked for: archived students are left out. Archiving is
     * how a student leaves without erasing history, so a group they were in must stop billing them —
     * but their past group lessons stay untouched.
     */
    fun bookableMembers(members: List<Student>): List<Student> = members.filterNot { it.archived }
}
