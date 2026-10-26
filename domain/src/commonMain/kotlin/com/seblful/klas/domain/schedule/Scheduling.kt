package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Rules for composing a valid class booking. */
object Scheduling {

    /**
     * The stretch of time a lesson occupies: when it starts and how long it runs.
     *
     * The span is **half-open** — [end] is the first instant that is free again — so two lessons back
     * to back, one ending exactly as the next starts, do not collide.
     *
     * A slot must have a **positive** length; this constructor enforces it for every caller, as
     * `SessionEdit`, `ClassRepository.bookSeries` and `BackupIntegrity` do at their own boundaries.
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

        val end: Instant get() = start.plus((durationMinutes * 60L).seconds)

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
     * The lookback [windowFor] uses to find lessons already running when a slot starts.
     *
     * The duration picker limits new lengths to 1 439 minutes, which this window covers even
     * across midnight. [Slot] and backup validation only require a positive duration; a longer
     * restored lesson can begin before this window and be missed by the conflict scan.
     */
    val LONGEST_LESSON: Duration = 1.days

    /**
     * The span of class **start** times [conflicts] must be given to answer correctly for [slot].
     *
     * Half-open, like [Slot]: [from] is included, [toExclusive] is not — a lesson beginning exactly as
     * the slot closes is back-to-back with it, not on top of it.
     */
    data class Window(val from: Instant, val toExclusive: Instant)

    /**
     * Start-time window for classes that could overlap [slot]. It reaches back by [LONGEST_LESSON] so a
     * class already in progress when the slot opens is included in the conflict scan.
     */
    fun windowFor(slot: Slot): Window = windowFor(listOf(slot))

    /**
     * One window covering **every** slot in [slots] — what a booking that writes a whole term reads over.
     *
     * The union of the per-slot windows is a superset of each, and [conflicts] applies the real overlap
     * test to every one, so handing it more classes than a given slot needs is the safe direction.
     *
     * Here rather than at the call site so the **write** path checks its hour against the same tested
     * window shape as the read path, not a hand-rolled copy that could drift from it.
     */
    fun windowFor(slots: List<Slot>): Window {
        require(slots.isNotEmpty()) { "A window covers at least one slot" }
        return Window(slots.minOf { it.start }.minus(LONGEST_LESSON), slots.maxOf { it.end })
    }

    /**
     * The lessons already in the schedule that a lesson at [slot] would collide with. Empty means the
     * time is free.
     *
     * There is one tutor, so a lesson overlapping one they are already teaching describes a time that
     * cannot happen — which is why this is a *rule* rather than a warning. Two things soften it:
     *
     * - a **cancelled** class gives its slot back (`LessonStatus.occupiesTime`) — the lesson it was is
     *   not happening, so that hour is free to re-book;
     * - the classes named in [ignoring] are skipped, which is how the lesson **being edited** avoids
     *   colliding with itself. A group lesson has to pass *every* member's class id, not just the one
     *   the tutor tapped, or re-saving it would clash with its own other members.
     *
     * Results come back as sessions ([SessionRules.sessions]), so a group lesson standing in the way
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
    ): List<ClassSession> = SessionRules.sessions(
        existing.filter {
            it.id !in ignoring &&
                it.status.occupiesTime &&
                it.durationMinutes > 0 &&
                slotOf(it).overlaps(slot)
        },
    )
}
