package com.seblful.klas.domain.reminders

import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.time.MINUTES_PER_DAY
import com.seblful.klas.domain.time.SECONDS_PER_MINUTE
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The app's three **digests** — a morning summary of the day's lessons, an evening list of the ones
 * nobody marked, and a weekly summary of who owes money — and everything about them that is a decision
 * rather than a platform call.
 *
 * ### Why these are workers and the lesson reminder is an alarm
 *
 * The split the app already draws is *whether being late is acceptable* (`ReminderAlarms`,
 * `WorkScheduler`). «Занятие через час» delivered two hours late is not a late reminder but a wrong one,
 * so it rides `AlarmManager`. A digest is the opposite: «сегодня 4 занятия» is still true and still useful
 * twenty minutes after nine, and a summary of who owes money does not become wrong by arriving at
 * half past. So both of these are ordinary deferrable work, and neither spends the app's one
 * exact-alarm budget — which is what keeps `SCHEDULE_EXACT_ALARM` about the one feature that needs it.
 *
 * ### Why the firing instant is computed here
 *
 * For the same reason `Reminders.Plan` carries an instant rather than a delay: the alternative is an
 * adapter re-deriving "the next 09:00" against a second clock read of its own, some moments after the one
 * the decision was made from. Here the whole of *when* is two pure functions, so the interesting cases —
 * the time already past today, the chosen weekday being today, a clock that springs forward over the hour
 * chosen — are answered on the JVM instead of by waiting a day on a device.
 *
 * ### Local time, not elapsed time
 *
 * Both are wall-clock promises: nine in the morning *here*, whatever that is in elapsed hours from now.
 * They resolve through [TimeZone], so the day a clock changes is 23 or 25 hours long and the digest still
 * lands at nine. A chosen minute that a spring-forward *deletes* — an hour that does not exist that day —
 * is shifted forward by the length of the gap, which is `java.time`'s own rule for a local time in a gap:
 * 02:30 fires at 03:30 on that one morning a year. The alternative worth avoiding is not the hour but the
 * throw: this is computed on a background path where an exception reports nothing to anybody, so the
 * failure would be the digest silently never happening again.
 */
object Digests {

    /**
     * When each digest should next fire — **null meaning "not at all"**, which is how a switched-off digest
     * is expressed rather than as a separate cancel call.
     *
     * One value carrying all three, for the reason `SettingsChange.replansDigests` is one arm covering
     * seven fields: they are always planned together from one settings read, and a plan that could
     * describe one of them and stay silent about another would let a caller re-plan part of it. An
     * adapter handed this arranges exactly what it says and cancels whatever it does not.
     */
    data class Plan(val agendaAt: Instant?, val unfinishedAt: Instant?, val debtsAt: Instant?)

    /**
     * Works out [Plan] from the tutor's settings — the whole of *when the digests happen*, in one pure
     * function.
     *
     * The parameters are the settings fields rather than the settings object, on the rule that keeps this
     * package free of Android and of the data layer (`Reminders.plan` takes a lead and a horizon the same
     * way). Seven of the nine are settings fields, because that is how many facts decide this; naming them
     * at the call site is what stops the three minutes-past-midnight values being passed in each other's
     * place.
     */
    fun plan(
        now: Instant,
        zone: TimeZone,
        agendaEnabled: Boolean,
        agendaMinuteOfDay: Int,
        unfinishedEnabled: Boolean,
        unfinishedMinuteOfDay: Int,
        debtsEnabled: Boolean,
        debtDayOfWeek: Int,
        debtMinuteOfDay: Int,
    ): Plan = Plan(
        agendaAt = if (agendaEnabled) nextDaily(now, zone, agendaMinuteOfDay) else null,
        // The evening digest is a second daily one and shares [nextDaily] with the morning's: which hour
        // it is set to is the tutor's, and nothing here privileges 21:00 over 09:00.
        unfinishedAt = if (unfinishedEnabled) nextDaily(now, zone, unfinishedMinuteOfDay) else null,
        debtsAt = if (debtsEnabled) {
            nextWeekly(now, zone, dayOfWeekOf(debtDayOfWeek), debtMinuteOfDay)
        } else {
            null
        },
    )

    /**
     * How long from [now] until [at] — what an adapter arranging a [Plan] has to hand its platform.
     *
     * **Clamped at zero**, which is a decision rather than defensiveness: an instant already past means
     * "this one is due now". [plan] cannot produce one (it only ever looks forward), but a caller acting on
     * a plan it read some moments ago can, and a negative delay is not a thing to hand a scheduler.
     *
     * It exists so the adapter never computes the delay from a **second clock read**, some moments after
     * the one the plan was decided from — the mistake `Plan` carries instants to avoid (see the class KDoc,
     * and `Reminders.Plan.fireAt`).
     */
    fun delayUntil(at: Instant, now: Instant): Duration =
        (at - now).coerceAtLeast(Duration.ZERO)

    /**
     * Whether something already arranged for [arranged] should be **left to happen** rather than cancelled
     * and replaced by [planned] — the "a digest may be late" rule, stated where the rest of *when* lives.
     *
     * True exactly when the arrangement is **due and has not happened yet** (at or before [now]) and the
     * plan has merely rolled past it. [plan] looks strictly forward, and `Maintenance` re-plans on every
     * app start, every six hours, after a reboot and behind every write — so a 09:00 agenda that WorkManager
     * had not run yet at 09:12 (Doze, or a restricted battery bucket) would be re-planned for *tomorrow*,
     * and the policy that applies a new plan would cancel the pending run on its way. Today's agenda would
     * never arrive and nothing would record that it was owed; for the debt digest it would cost a whole
     * week.
     *
     * Turning *late* into *never* is the one outcome that choosing workers over alarms was meant to avoid,
     * since «сегодня 4 занятия» is still true at twenty past.
     *
     * Two consequences worth stating, because both are deliberate:
     * - **Switching a digest off still cancels a due one.** [planned] is null then, so this is false.
     * - **Changing the hour lets the old one through, once.** Moving 09:00 to 10:00 at 09:12 leaves this
     *   morning's 09:00 digest to arrive late; the new hour applies from the next occurrence. It was owed
     *   under the setting in force when it came due, and delivering it late is closer to the tutor's
     *   instruction than dropping it.
     */
    fun stillOwed(arranged: Instant?, planned: Instant?, now: Instant): Boolean =
        arranged != null && planned != null && !(arranged > now) && planned > arranged

    /**
     * What should become of one digest's arrangement — the whole of the decision, in one value.
     *
     * The three answers — a null instant means *take down what is enqueued*, [stillOwed] means *do not
     * touch it*, anything else is a re-enqueue — are named here rather than in the scheduler adapter,
     * because the adapter is WorkManager code that no JVM test can reach and two of the branches have
     * already produced a defect (see [stillOwed]). The adapter is left with a `when` that turns each answer
     * into the platform call for it and decides nothing.
     *
     * @param planned when this digest should next fire, or null when it is switched off.
     * @param arranged when the run already enqueued is next due, or null when nothing is waiting. Only
     *   consulted for the leave-alone rule.
     * @param every how often the digest repeats after its first run — a day for the agenda, a week for the
     *   debts. Carried through rather than decided here: it is a property of *which* digest this is, and
     *   the caller is the one that knows.
     */
    fun arrangementFor(
        planned: Instant?,
        arranged: Instant?,
        now: Instant,
        every: Duration,
    ): Arrangement = when {
        planned == null -> Arrangement.Cancel
        stillOwed(arranged = arranged, planned = planned, now = now) -> Arrangement.LeaveAlone
        else -> Arrangement.Enqueue(delay = delayUntil(planned, now), every = every)
    }

    /** What [arrangementFor] came to. Three answers, because three different things happen to the queue. */
    sealed interface Arrangement {

        /**
         * Take down whatever is enqueued: the digest is off.
         *
         * Not "do nothing" — a digest switched off has to have its pending run removed, or it goes on
         * arriving after the switch that governs it was turned off.
         */
        data object Cancel : Arrangement

        /**
         * Leave the queue exactly as it is: a run is already due and has not happened.
         *
         * This is the arm that turns *late* into *arrived* rather than into *never* — see [stillOwed].
         */
        data object LeaveAlone : Arrangement

        /**
         * Replace what is enqueued with a run in [delay], repeating every [every].
         *
         * A delay rather than an instant because that is what a scheduler takes, and it is computed here
         * from the one clock read the caller made — see [delayUntil].
         */
        data class Enqueue(val delay: Duration, val every: Duration) : Arrangement
    }

    /**
     * The next instant a daily digest set for [minuteOfDay] fires, **strictly after** [now].
     *
     * Strictly, because this is called from the worker that has just fired one: an inclusive comparison
     * would re-enqueue the same instant, and the digest would post repeatedly until the minute passed.
     */
    fun nextDaily(now: Instant, zone: TimeZone, minuteOfDay: Int): Instant {
        val at = timeOf(minuteOfDay)
        val today = now.toLocalDateTime(zone).date
        val candidate = today.atTime(at).toInstant(zone)
        return if (candidate > now) candidate
        else today.plus(1, DateTimeUnit.DAY).atTime(at).toInstant(zone)
    }

    /**
     * The next instant a weekly digest set for [dayOfWeek] at [minuteOfDay] fires, strictly after [now].
     *
     * `nextOrSame` and then a week's step, rather than always jumping to the next occurrence: the chosen
     * day being *today* is the ordinary case for half of a Monday, and skipping to next Monday would make
     * switching the digest on that morning silently cost a week.
     */
    fun nextWeekly(now: Instant, zone: TimeZone, dayOfWeek: DayOfWeek, minuteOfDay: Int): Instant {
        val at = timeOf(minuteOfDay)
        val today = now.toLocalDateTime(zone).date
        val daysAhead = (dayOfWeek.isoDayNumber - today.dayOfWeek.isoDayNumber).mod(CalendarLayout.DAYS_IN_WEEK)
        val thisWeek = today.plus(daysAhead, DateTimeUnit.DAY)
        val candidate = thisWeek.atTime(at).toInstant(zone)
        return if (candidate > now) candidate
        else thisWeek.plus(1, DateTimeUnit.WEEK).atTime(at).toInstant(zone)
    }

    /**
     * The stored minute-past-midnight as a wall-clock time, clamped to the day's valid range.
     *
     * An invalid value is clamped to the nearest end of the day so a malformed setting does not stop
     * digest scheduling.
     */
    fun timeOf(minuteOfDay: Int): LocalTime =
        LocalTime.fromSecondOfDay(minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) * SECONDS_PER_MINUTE)

    /**
     * The stored weekday, falling back to Monday when the value is outside 1..7.
     */
    fun dayOfWeekOf(value: Int): DayOfWeek =
        if (value in 1..DayOfWeek.entries.size) DayOfWeek(value) else DayOfWeek.MONDAY

    /**
     * What the morning digest has to say about a day.
     *
     * @param day the day it is about — which is also where a tap on it opens the schedule.
     * @param summary the same [DaySummary] the schedule prints under its stepper — deliberately the same
     *   number, so «4 занятия · 5 ч · 240 BYN» in the tray and in the app cannot disagree.
     * @param lessons the day's lessons in start order, one entry per lesson (a group lesson is one, named
     *   by the group), so the tray shows the tutor's morning as the schedule would list it.
     */
    data class Agenda(val day: LocalDate, val summary: DaySummary, val lessons: List<Entry>) {

        data class Entry(val start: Instant, val subject: String, val who: String)

        /**
         * Whether there is anything worth waking the tutor's phone for.
         *
         * A day with **only cancelled lessons is not worth posting**, though [DaySummary] counts it as
         * non-empty: the digest's promise is what is on today, and "nothing, one lesson was called off"
         * is a fact the tutor already knows — they are the one who cancelled it. An empty day says
         * nothing at all rather than «0 занятий», which is a notification whose entire content is that it
         * did not need to be sent.
         */
        val isWorthPosting: Boolean get() = summary.lessons > 0
    }

    /**
     * What the evening digest has to say: the lessons that ended today and that nobody has marked.
     *
     * "Unfinished" is [LessonPhase.OVERDUE] — finished and still `SCHEDULED` — which is the same question
     * auto-completion asks (`ClassRules.shouldAutoComplete`). The two are the app's two answers to it: with
     * auto-completion on the app assumes the lesson happened and bills it, and with it off the lesson sits
     * there being neither taught nor called off, which is money the reports do not yet know about.
     *
     * **A count and the lessons, not a summary.** [Agenda] prints `DaySummary` because the morning
     * question is "how big is today"; this one is a to-do list, and a total of what the day *would* come
     * to would be stating income that has not been recognised.
     *
     * @param day the day they were taught on — where a tap on the digest opens the schedule, even after
     *   midnight.
     * @param lessons the unmarked lessons in start order, one entry per lesson — the same [Agenda.Entry]
     *   the morning digest lists, because a line naming a lesson reads the same way at either end of the
     *   day and two shapes for it would be two ways to word one thing.
     */
    data class Unfinished(val day: LocalDate, val lessons: List<Agenda.Entry>) {

        val count: Int get() = lessons.size

        /**
         * Nothing unmarked, nothing to say — the ordinary evening for a tutor who marks lessons as they
         * go, and the whole reason this is not a fixed daily report.
         */
        val isWorthPosting: Boolean get() = lessons.isNotEmpty()
    }

    /**
     * What the weekly digest has to say about money owed.
     *
     * @param students how many **active** students are in debt. Archived students are excluded upstream,
     *   by the same rule reports use — chasing a retired student's balance is not what this is for.
     * @param total every positive balance added up; credits are not netted off against it
     *   (`Reports.totalOutstanding`), because an overpaid student does not reduce what another one owes.
     */
    data class Debts(val students: Int, val total: Money) {

        /** Nothing owed, nothing to say. The whole point of the digest is the number being non-zero. */
        val isWorthPosting: Boolean get() = students > 0
    }
}
