package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.reminders.Reminders
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One day as the booking form steps through it: the stretch from [start] to [end] — whole hours, inside
 * [day] — and the lessons already [booked] in it, which a lesson put there would clash with. Every day
 * runs from [FIRST_HOUR] to [END_HOUR], widened to whole hours around any lesson outside them: where the
 * first free time is looked for. A step reaches the whole [day].
 *
 * Hours are counted in elapsed time from midnight, as booking counts lengths, so a day the clocks change
 * on draws 23 or 25 of them and each line still falls on a whole local hour.
 */
data class DayTimeline(
    val day: DayBounds,
    val start: Instant,
    val end: Instant,
    val booked: List<Lesson>,
) {
    /**
     * A lesson already on the day: when, [who] it is for — named as a reminder names it — whose it is, for
     * the colour, and where.
     */
    data class Lesson(val slot: Scheduling.Slot, val who: String, val assignee: Assignee, val mode: ClassMode) {
        companion object {
            fun of(session: ClassSession, groupName: String?, studentName: String?): Lesson = Lesson(
                slot = Scheduling.slotOf(session.first),
                who = Reminders.announcedBy(groupName, studentName),
                assignee = session.groupId?.let(Assignee::Group) ?: Assignee.Student(session.first.studentId),
                mode = session.first.mode,
            )
        }
    }

    val minutes: Int get() = (end - start).inWholeMinutes.toInt()

    /** This timeline, grown by whole hours to show [slot] — the lesson being booked — where it overlaps the day. */
    fun including(slot: Scheduling.Slot?): DayTimeline {
        if (slot == null || slot.end <= day.start || slot.start >= day.end) return this
        return copy(
            start = minOf(start, floorHour(slot.start).coerceAtLeast(day.start)),
            end = maxOf(end, ceilHour(slot.end).coerceAtMost(day.end)),
        )
    }

    /**
     * Where a lesson of [minutes] starts if put at [at]: the [SNAP_MINUTES] step under it, moved just as
     * far as it takes to fit the free stretch around it — so a time near a lesson books back to back with
     * it. Null on a booked lesson, outside the timeline, or in a gap too short for the lesson.
     */
    fun place(at: Instant, minutes: Int): Instant? {
        val busy = booked.map { it.slot }
        val free = at >= start && at < end && busy.none { at >= it.start && at < it.end }
        val gapStart = maxOf(start, busy.filter { it.end <= at }.maxOfOrNull { it.end } ?: start)
        val gapEnd = minOf(end, busy.filter { it.start > at }.minOfOrNull { it.start } ?: end)
        val length = minutes.minutes
        val snapped = start + ((at - start).inWholeMinutes / SNAP_MINUTES * SNAP_MINUTES).minutes
        return if (free && gapEnd - gapStart >= length) snapped.coerceIn(gapStart, gapEnd - length) else null
    }

    /**
     * Where a lesson of [minutes] at [from] goes when moved [by] — a step either way: that far if it fits
     * whole in free time there, else on past the lesson in the way to the nearest start that way that does.
     * It never comes back to [from], so a lesson back to back with another still moves.
     * Null when nothing that way is free.
     */
    fun shifted(from: Instant, by: Duration, minutes: Int): Instant? {
        val length = minutes.minutes
        val to = from + by
        val fits = freeGaps().filter { (gapStart, gapEnd) -> gapEnd - gapStart >= length }
        return if (by.isPositive()) {
            fits.filter { (_, gapEnd) -> to <= gapEnd - length }.minOfOrNull { (gapStart, _) -> maxOf(gapStart, to) }
        } else {
            fits.filter { (gapStart, _) -> to >= gapStart }.maxOfOrNull { (_, gapEnd) -> minOf(gapEnd - length, to) }
        }
    }

    /** The stretches of the timeline no lesson is in, in order — lessons that overlap make one busy stretch. */
    private fun freeGaps(): List<Pair<Instant, Instant>> {
        val gaps = mutableListOf<Pair<Instant, Instant>>()
        var free = start
        booked.map { it.slot }.sortedBy { it.start }.forEach { busy ->
            if (busy.start > free) gaps += free to minOf(busy.start, end)
            free = maxOf(free, busy.end)
        }
        if (free < end) gaps += free to end
        return gaps
    }

    /**
     * The first start, at or after [notBefore], a lesson of [minutes] can be [place]d at — what the timeline
     * offers before the tutor has picked a time. Null when nothing that long is free.
     */
    fun firstFree(minutes: Int, notBefore: Instant): Instant? {
        val from = maxOf(start, notBefore)
        val steps = ((from - start).inWholeMinutes + SNAP_MINUTES - 1) / SNAP_MINUTES
        return generateSequence(start + (steps * SNAP_MINUTES).minutes) { it + SNAP_MINUTES.minutes }
            .takeWhile { it < end }
            .firstNotNullOfOrNull { at -> place(at, minutes)?.takeIf { it >= notBefore } }
    }

    private fun floorHour(at: Instant): Instant = day.start + ((at - day.start).inWholeHours).hours

    private fun ceilHour(at: Instant): Instant = floorHour(at).let { if (it == at) it else it + 1.hours }

    companion object {
        /** The grid [place] and [firstFree] put starts on. */
        const val SNAP_MINUTES = 5

        /** The hour every day starts at, before its lessons widen it. */
        const val FIRST_HOUR = 9

        /** The hour every day ends at, before its lessons widen it. */
        const val END_HOUR = 23

        /**
         * [date] from [FIRST_HOUR] to [END_HOUR], widened to show every lesson [booked] on it. [booked] are
         * the day's lessons other than the one being edited — `Scheduling.conflicts` over the whole day.
         */
        fun of(date: LocalDate, zone: TimeZone, booked: List<Lesson>): DayTimeline {
            val opening = DayTimeline(
                day = DayBounds.of(date, zone),
                start = date.atTime(FIRST_HOUR, 0).toInstant(zone),
                end = date.atTime(END_HOUR, 0).toInstant(zone),
                booked = booked,
            )
            return booked.fold(opening) { timeline, lesson -> timeline.including(lesson.slot) }
        }
    }
}
