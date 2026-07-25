package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import java.time.Instant

/** Rules for composing a valid class booking. */
object Scheduling {

    /**
     * The stretch of time a lesson occupies: when it starts and how long it runs.
     *
     * The span is **half-open** — [end] is the first instant that is free again — so two lessons back
     * to back, one ending exactly as the next starts, do not collide.
     */
    data class Slot(val start: Instant, val durationMinutes: Int) {

        val end: Instant get() = start.plusSeconds(durationMinutes * 60L)

        fun overlaps(other: Slot): Boolean = start < other.end && other.start < end
    }

    /** The slot [c] occupies. */
    fun slotOf(c: TutoringClass): Slot = Slot(c.start, c.durationMinutes)

    /**
     * The subjects that may be chosen when booking a class for a student: only those with a price
     * set for that student (passed in [pricedSubjectIds]). A completed class snapshots its
     * subject's price, so a class for an unpriced subject could never be billed — the price must be
     * added first. The order of [allSubjects] is preserved so the picker stays alphabetical.
     */
    fun assignableSubjects(allSubjects: List<Subject>, pricedSubjectIds: Collection<Long>): List<Subject> {
        val priced = pricedSubjectIds.toSet()
        return allSubjects.filter { it.id in priced }
    }

    /**
     * The lessons already in the schedule that a lesson at [slot] would collide with. Empty means the
     * time is free.
     *
     * There is one tutor, so a lesson overlapping one they are already teaching describes a time that
     * cannot happen — which is why this is a *rule* rather than a warning. Two things soften it:
     *
     * - a **cancelled** class gives its slot back (`ClassStatus.occupiesTime`) — the lesson it was is
     *   not happening, so that hour is free to re-book;
     * - the classes named in [ignoring] are skipped, which is how the lesson **being edited** avoids
     *   colliding with itself. A group lesson has to pass *every* member's class id, not just the one
     *   the tutor tapped, or re-saving it would clash with its own other members.
     *
     * Results come back as sessions ([GroupRules.sessions]), so a group lesson standing in the way
     * reads as the one lesson it is rather than as one clash per member.
     */
    fun conflicts(
        slot: Slot,
        existing: List<TutoringClass>,
        ignoring: Set<Long> = emptySet(),
    ): List<ClassSession> = GroupRules.sessions(
        existing.filter { it.id !in ignoring && it.status.occupiesTime && slotOf(it).overlaps(slot) },
    )
}
