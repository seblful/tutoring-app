package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.Taught
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A day's lesson count, teaching time, expected charges, and cancelled lesson count. Group lessons count
 * once for time and lesson totals; money is summed per student who owes. Cancelled lessons contribute only
 * to [cancelled].
 *
 * @param lessons lessons the day holds — **not** classes, so a group lesson taught to three is one.
 * @param minutes how long they run: the tutor's time, so that same group lesson counts its 90 minutes
 *   once rather than three times.
 * @param money what the day's lessons come to, summed **per student**, because that is who owes it.
 * @param cancelled lessons called off, counted apart from the rest and included in nothing above.
 */
data class DaySummary(
    val lessons: Int,
    val minutes: Int,
    val money: Money,
    val cancelled: Int,
) {
    /** Whether there is nothing at all to report — no lesson on, none called off. */
    val isEmpty: Boolean get() = lessons == 0 && cancelled == 0

    /**
     * Displayable parts in order. Empty days return no parts; days containing only cancelled lessons
     * return only the cancellation count. Money includes expected charges for scheduled lessons.
     */
    val parts: List<Part>
        get() = when {
            isEmpty -> emptyList()
            lessons > 0 -> listOfNotNull(
                Part.Lessons(lessons),
                Part.Hours(minutes),
                Part.Earned(money),
                Part.Cancelled(cancelled).takeIf { cancelled > 0 },
            )
            else -> listOf(Part.Cancelled(cancelled))
        }

    /**
     * One clause of a day's sentence — the figure and which figure it is, leaving the words to whichever
     * surface is printing it (`format/daySummaryText` for both of them today).
     */
    sealed interface Part {
        /** How many lessons the day holds. Printed through `<plurals>`, so it carries its own count. */
        data class Lessons(val count: Int) : Part

        /** How long they run, in minutes — the tutor's time. */
        data class Hours(val minutes: Int) : Part

        /** What they come to, billed or not yet. */
        data class Earned(val money: Money) : Part

        /** How many were called off. Only ever present when [count] is above zero. */
        data class Cancelled(val count: Int) : Part
    }

    companion object {
        val NONE = DaySummary(lessons = 0, minutes = 0, money = Money.ZERO, cancelled = 0)

        /**
         * Summarizes one day's lessons, already collapsed by [SessionRules.sessions].
         */
        fun of(sessions: List<ClassSession>): DaySummary {
            val (off, on) = sessions.partition { it.status == LessonStatus.CANCELLED }
            return DaySummary(
                lessons = on.size,
                minutes = on.sumOf { it.first.durationMinutes },
                money = on.flatMap { it.classes }
                    .filter { it.attended.bills }
                    .fold(Money.ZERO) { acc, c -> acc + ClassRules.chargeFor(c.price, c.durationMinutes) },
                cancelled = off.size,
            )
        }
    }
}

/**
 * Where **now** falls among a day's lessons — the mark the schedule draws to say "you are here".
 *
 * A day list is a column of times with nothing saying which of them have been and which are ahead. The
 * tutor knows, of course; what they cannot do at a glance is find the next one, which is the only row on
 * the screen they can still act on.
 *
 * Pure, and outside the composable that draws it, for the reason every rule in this app is: the
 * interesting cases are the boundaries — the marker on a day that is not today, on an empty day, before
 * the first lesson, after the last — and none of them can be exercised on a device without waiting for
 * the clock to reach them.
 */
object NowMarker {

    /**
     * The index in [sessions] the mark sits **before**, or null when this day has no mark to draw.
     *
     * `sessions.size` is a legitimate answer and means "after the last one": the day is done. Null means
     * the day is not the one [now] falls in — a past or future day has no "now" in it, and drawing the
     * mark at either end of one would claim otherwise.
     *
     * @param sessions the day's lessons **in start order**. Not defended against, because the answer is a
     *   count and a count is only an index in a sorted list: every caller has been through
     *   [SessionRules.sessions], which sorts by start, and re-checking that on every minute tick would cost
     *   more than the bug it guards. An unsorted list gets a plausible wrong answer — so keep them sorted.
     * @param day the day being drawn, which is what makes this answerable at all: a list of lessons
     *   cannot say which day it is when it is empty.
     */
    fun positionIn(
        sessions: List<ClassSession>,
        day: LocalDate,
        now: Instant,
        zone: TimeZone,
    ): Int? {
        if (day != now.toLocalDateTime(zone).date) return null
        // Counts the lessons that have started rather than searching for the next one, so a day whose
        // lessons are all past lands at `size` and one whose lessons are all ahead lands at 0.
        return sessions.count { !(it.start > now) }
    }
}
