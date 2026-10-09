package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.Taught
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What one day of the schedule adds up to: how many lessons are on it, how long they run, and what
 * they come to.
 *
 * The schedule could say which lessons a Tuesday held and not what Tuesday *was*. Counting four rows,
 * adding four durations and four rates is arithmetic the tutor was left to do on the screen that exists
 * to answer "how full is this day" — and the money half of it is the question the app is for.
 *
 * ### The third thing a day is counted as
 *
 * Three types count the same classes and none of them can be substituted for another, so each says
 * which question it answers:
 *
 * - [CalendarLayout.DayLoad] — **one number per status**, for a calendar *cell*: how the day divides.
 * - [Taught.Totals] — a *card's* totals over any span, and deliberately **backward-looking**: `lessons`
 *   and `minutes` count only what was taught, `earned` only what was billed. Right for "what has this
 *   student's year come to", wrong for a day: a tutor opening tomorrow would read «0».
 * - this — **what a day holds**, taught or still to come.
 *
 * ### The money is what the day comes to, billed or not
 *
 * [money] is each class's rate scaled by its length ([ClassRules.chargeFor]) rather than its recorded
 * [charge][com.seblful.klas.domain.model.TutoringClass.charge], because a charge is `Money.ZERO` until
 * the lesson is marked taught. For a lesson that has been, the two are the same number by construction
 * (`ClassRules.rebill` derives one from the other), so this is not a second opinion about billed
 * history — it is the same arithmetic reaching one day earlier.
 *
 * A **cancelled lesson counts for nothing** and is reported separately ([cancelled]): it gave its hour
 * back, which is the whole reason a cancelled card recedes in the list. And within a lesson that did go
 * ahead, only the students who owe for it are summed — an absence that stops a charge
 * ([Attended.bills][com.seblful.klas.domain.model.Attended.bills]) stops this too.
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
     * What this day's sentence is made of, in order — «4 занятия · 5 ч · 240 BYN» as four decisions rather
     * than as a string.
     *
     * Two rules live here and nowhere else, and both used to be buried in the function that did the
     * wording (`format/daySummaryText`), which takes `Resources` and therefore could not be reached from a
     * JVM test:
     *
     * - **Empty is empty.** A day with nothing on and nothing called off returns no parts at all, so a
     *   caller has to decide what "nothing to say" looks like rather than being handed «0 занятий · 0 ч».
     * - **A day that was entirely called off says only that.** There are no hours and no money on such a
     *   day, and printing zeroes for them would dress a cancelled day up as a counted one.
     *
     * This matters more than an ordinary extraction because the sentence has **two readers** — the
     * schedule's day heading and the morning digest — and a digest that added a day up differently from
     * the screen it summarises would be the worst kind of wrong: both numbers plausible, one of them the
     * tutor's actual morning. That agreement is now a property of a pure value the two share, rather than
     * of one Android-bound function they both happen to call.
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
         * Sums [sessions] — one day's lessons, already collapsed by [SessionRules.sessions].
         *
         * Takes sessions rather than classes because every caller has them: the schedule collapses a
         * day's classes into rows before it draws them, and collapsing twice is what
         * [Taught.bySessions] exists to avoid.
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
        zone: ZoneId,
    ): Int? {
        if (day != now.atZone(zone).toLocalDate()) return null
        // Counts the lessons that have started rather than searching for the next one, so a day whose
        // lessons are all past lands at `size` and one whose lessons are all ahead lands at 0.
        return sessions.count { !it.start.isAfter(now) }
    }
}
