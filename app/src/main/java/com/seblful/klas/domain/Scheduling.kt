package com.seblful.klas.domain

import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/** Rules for composing a valid class booking. */
object Scheduling {

    /**
     * The stretch of time a lesson occupies: when it starts and how long it runs.
     *
     * The span is **half-open** — [end] is the first instant that is free again — so two lessons back
     * to back, one ending exactly as the next starts, do not collide.
     *
     * A slot must have a **positive** length, and this is the one place in the app that insists on it.
     * The reason is that [overlaps] stops being a coherent question otherwise. A zero-length slot has
     * `end == start`, so it collides with a lesson that strictly *contains* it (10:30 inside 10:00–11:00)
     * but **not** with one starting at the same instant (10:00 against 10:00–11:00) — the half-open
     * comparison needs `other.start < end`, and there `other.start == end`. So the one-tutor-one-lesson
     * rule holds in the middle of an hour and quietly fails open at its edge, which is the worse of the
     * two: the form still shows the time as free. A negative length inverts the span outright.
     *
     * Refusing here rather than in the booking form is what makes that unrepresentable instead of
     * merely unlikely: the form already validates the range (`ClassForm.durationMinutes` is null unless
     * it is positive), but the form is not the only way in — the repository, a restored backup and the
     * schedule's move all build slots too, and each of them would otherwise have to remember.
     */
    data class Slot(val start: Instant, val durationMinutes: Int) {

        init {
            require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
        }

        val end: Instant get() = start.plusSeconds(durationMinutes * 60L)

        fun overlaps(other: Slot): Boolean = start < other.end && other.start < end
    }

    /**
     * The slot [c] occupies.
     *
     * @throws IllegalArgumentException if the class has no positive duration — see [Slot]. Callers
     *   reading *stored* classes should skip such rows instead of asking (as [conflicts] does): one
     *   unreadable row must not take the overlap check down for every other lesson.
     */
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
     *
     * A stored class with no positive duration is skipped rather than asked about. It occupies no time
     * by its own arithmetic, so it can stand in nobody's way — and this runs over rows from the
     * database, inside the flow a booking form collects, where letting one malformed row throw would
     * stop the overlap check being made *at all*. The row should not exist (a restored backup carrying
     * one is refused, see `BackupIntegrity`); if one does, the rule keeps working for every other
     * lesson.
     */
    fun conflicts(
        slot: Slot,
        existing: List<TutoringClass>,
        ignoring: Set<Long> = emptySet(),
    ): List<ClassSession> = GroupRules.sessions(
        existing.filter {
            it.id !in ignoring &&
                it.status.occupiesTime &&
                it.durationMinutes > 0 &&
                slotOf(it).overlaps(slot)
        },
    )
}
