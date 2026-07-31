package com.seblful.klas.domain

import com.seblful.klas.domain.model.Money
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The app's two **digests** — a morning summary of the day's lessons, and a weekly summary of who owes
 * money — and everything about them that is a decision rather than a platform call.
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
 * They resolve through [ZoneId], so the day a clock changes is 23 or 25 hours long and the digest still
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
     * One value carrying both, for the reason `SettingsChange.replansDigests` is one arm covering five
     * fields: the two are always planned together from one settings read, and a plan that could describe
     * one of them and stay silent about the other would let a caller re-plan half. An adapter handed this
     * arranges exactly what it says and cancels whatever it does not.
     */
    data class Plan(val agendaAt: Instant?, val debtsAt: Instant?)

    /**
     * Works out [Plan] from the tutor's settings — the whole of *when the digests happen*, in one pure
     * function.
     *
     * The parameters are the settings fields rather than the settings object, on the rule that keeps this
     * package free of Android and of the data layer (`Reminders.plan` takes a lead and a horizon the same
     * way). It is six of them because that is genuinely how many facts decide this; naming them at the
     * call site is what stops the two minutes-past-midnight values being passed in each other's place.
     */
    fun plan(
        now: Instant,
        zone: ZoneId,
        agendaEnabled: Boolean,
        agendaMinuteOfDay: Int,
        debtsEnabled: Boolean,
        debtDayOfWeek: Int,
        debtMinuteOfDay: Int,
    ): Plan = Plan(
        agendaAt = if (agendaEnabled) nextDaily(now, zone, agendaMinuteOfDay) else null,
        debtsAt = if (debtsEnabled) {
            nextWeekly(now, zone, dayOfWeekOf(debtDayOfWeek), debtMinuteOfDay)
        } else {
            null
        },
    )

    /**
     * How long from [now] until [at] — what an adapter arranging a [Plan] has to hand its platform, and
     * therefore the last piece of *when* that was living outside this module.
     *
     * **Clamped at zero**, which is a decision rather than defensiveness: an instant already past means
     * "this one is due now". [plan] cannot produce one (it only ever looks forward), but a caller acting on
     * a plan it read some moments ago can, and a negative delay is not a thing to hand a scheduler.
     *
     * It exists because the alternative was the adapter computing `Duration.between(Instant.now(), at)` —
     * a **second clock read**, some moments after the one the plan was decided from, and the exact mistake
     * `Plan` carries instants to avoid (see the class KDoc, and `Reminders.Plan.fireAt`). That read was
     * also the app's only remaining `Instant.now()`, which is to say the one place the shipped path could
     * not be the tested one.
     */
    fun delayUntil(at: Instant, now: Instant): Duration =
        Duration.between(now, at).coerceAtLeast(Duration.ZERO)

    /**
     * Whether something already arranged for [arranged] should be **left to happen** rather than cancelled
     * and replaced by [planned] — the "a digest may be late" rule, stated where the rest of *when* lives.
     *
     * True exactly when the arrangement is **due and has not happened yet** (at or before [now]) and the
     * plan has merely rolled past it. That is the whole of it, and it is the case the app was getting
     * wrong: [plan] looks strictly forward, and [Maintenance] re-plans on every app start, every thirty
     * minutes, after a reboot and behind every write — so a 09:00 agenda that WorkManager had not run yet
     * at 09:12 (Doze, or a restricted battery bucket) was re-planned for *tomorrow*, and the policy that
     * applies a new plan cancelled the pending run on its way. Today's agenda then never arrived, nothing
     * recorded that it was owed, and the most likely trigger was the tutor opening the app. For the debt
     * digest it lost a whole week.
     *
     * That inverts this module's own argument. These are workers precisely because «сегодня 4 занятия» is
     * still true at twenty past; turning *late* into *never* is the one outcome the choice was made to
     * avoid.
     *
     * Two consequences worth stating, because both are deliberate:
     * - **Switching a digest off still cancels a due one.** [planned] is null then, so this is false.
     * - **Changing the hour lets the old one through, once.** Moving 09:00 to 10:00 at 09:12 leaves this
     *   morning's 09:00 digest to arrive late; the new hour applies from the next occurrence. It was owed
     *   under the setting in force when it came due, and delivering it late is closer to the tutor's
     *   instruction than dropping it.
     */
    fun stillOwed(arranged: Instant?, planned: Instant?, now: Instant): Boolean =
        arranged != null && planned != null && !arranged.isAfter(now) && planned.isAfter(arranged)

    /**
     * The next instant a daily digest set for [minuteOfDay] fires, **strictly after** [now].
     *
     * Strictly, because this is called from the worker that has just fired one: an inclusive comparison
     * would re-enqueue the same instant, and the digest would post repeatedly until the minute passed.
     */
    fun nextDaily(now: Instant, zone: ZoneId, minuteOfDay: Int): Instant {
        val at = timeOf(minuteOfDay)
        val today = now.atZone(zone).toLocalDate()
        val candidate = today.atTime(at).atZone(zone).toInstant()
        return if (candidate.isAfter(now)) candidate
        else today.plusDays(1).atTime(at).atZone(zone).toInstant()
    }

    /**
     * The next instant a weekly digest set for [dayOfWeek] at [minuteOfDay] fires, strictly after [now].
     *
     * `nextOrSame` and then a week's step, rather than always jumping to the next occurrence: the chosen
     * day being *today* is the ordinary case for half of a Monday, and skipping to next Monday would make
     * switching the digest on that morning silently cost a week.
     */
    fun nextWeekly(now: Instant, zone: ZoneId, dayOfWeek: DayOfWeek, minuteOfDay: Int): Instant {
        val at = timeOf(minuteOfDay)
        val thisWeek = now.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(dayOfWeek))
        val candidate = thisWeek.atTime(at).atZone(zone).toInstant()
        return if (candidate.isAfter(now)) candidate
        else thisWeek.plusWeeks(1).atTime(at).atZone(zone).toInstant()
    }

    /**
     * The stored minute-past-midnight as a wall-clock time — **total**, like every other read of stored
     * state in this app (`ClassStatus.parse`, `Currency.parse`).
     *
     * `LocalTime.ofSecondOfDay` throws outside the day, and this is read on a background path where a
     * throw reports nothing to anybody: the digest would simply stop happening. Clamping puts an
     * impossible stored value at one end of the day it was trying to name, which is wrong in a way the
     * tutor can see and correct.
     */
    fun timeOf(minuteOfDay: Int): LocalTime =
        LocalTime.MIDNIGHT.plusMinutes(minuteOfDay.coerceIn(0, MINUTES_IN_DAY - 1).toLong())

    /**
     * The stored weekday, **falling back to Monday** — total for [timeOf]'s reason. `DayOfWeek.of` throws
     * outside 1..7, and a digest that stopped firing because of one bad integer would be a feature
     * disappearing in silence. Monday is the default the column ships with.
     */
    fun dayOfWeekOf(value: Int): DayOfWeek =
        if (value in 1..7) DayOfWeek.of(value) else DayOfWeek.MONDAY

    /**
     * What the morning digest has to say about a day.
     *
     * @param summary the same [DaySummary] the schedule prints under its stepper — deliberately the same
     *   number, so «4 занятия · 5 ч · 240 BYN» in the tray and in the app cannot disagree.
     * @param lessons the day's lessons in start order, one entry per lesson (a group lesson is one, named
     *   by the group), so the tray shows the tutor's morning as the schedule would list it.
     */
    data class Agenda(val summary: DaySummary, val lessons: List<Entry>) {

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

    private const val MINUTES_IN_DAY = 24 * 60
}
