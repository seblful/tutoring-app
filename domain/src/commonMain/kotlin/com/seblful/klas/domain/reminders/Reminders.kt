package com.seblful.klas.domain.reminders

import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Decides which upcoming classes deserve a reminder notification and when it should fire. */
object Reminders {

    /**
     * A planned reminder: which class, the **instant** it fires, and whether that instant has already
     * arrived.
     *
     * An absolute instant, not a delay. The delay was WorkManager's shape (`setInitialDelay`), kept
     * after reminders became alarms — and it made the only adapter reconstruct the firing time
     * against a second clock read of its own, some moments after the one the plan was derived from.
     * When a reminder fires is this module's decision; an adapter arms what it is handed.
     *
     * [due] is that "already arrived" — the one fact the adapter cannot read off the instant alone
     * without a clock of its own, and the fact it needs to tell "arm this" from "this one is owed".
     * See [plan].
     */
    data class Plan(val classId: Long, val fireAt: Instant, val due: Boolean = false)

    /**
     * How long before a lesson its reminder fires, until the tutor picks otherwise — in minutes, because
     * that is how the setting is stored and how the calendar's email reminder is written.
     *
     * A `const` so the settings row, the settings model and the calendar port can all default to it: the
     * figure was written out four times, and a default changed in one of them would have left a fresh
     * install's in-app and email reminders disagreeing about when "an hour before" is.
     */
    const val DEFAULT_LEAD_MINUTES: Int = 60

    val DEFAULT_LEAD: Duration = (DEFAULT_LEAD_MINUTES.toLong()).minutes
    val DEFAULT_HORIZON: Duration = 7.days

    /** Read far enough ahead to arm a week's reminders even when the lead itself spans several days. */
    fun planningHorizon(lead: Duration): Duration = DEFAULT_HORIZON + lead

    /**
     * Plans a reminder for every lesson still worth hearing about at [now] that starts within
     * [horizon] — one that is [LessonPhase.UPCOMING], and one that is [LessonPhase.IN_PROGRESS], since
     * a reminder that arrives after the hour has begun is still a warning about a lesson the tutor can
     * still do something about ([shouldPost] keeps posting until it ends). Each fires [lead] before the
     * lesson start. Past, cancelled and completed classes are skipped — only a class that will still
     * happen earns a reminder.
     *
     * A lesson whose lead moment has **already passed** is still planned, but as [Plan.due] rather than
     * as something to arm. Two obligations meet in that one entry, and this is where they are told
     * apart:
     *
     * - **It must not be armed.** The plan is re-derived on every class change, every app start and
     *   every maintenance tick, so setting an instant that has already passed fires on the spot and
     *   would announce the same imminent lesson again on each of those — the tutor books a class for
     *   the next hour and is told about it right away, then told again the next time anything changes.
     *   A reminder is a warning *ahead* of a lesson; a moment behind us is not a moment to set an
     *   alarm for.
     * - **It must not be taken away either.** The adapter tunes the alarms to this plan by cancelling
     *   what it armed before (`ReminderAlarms.set`), and an alarm set for this lesson may not have
     *   fired yet: where `SCHEDULE_EXACT_ALARM` is not granted the alarm is inexact by design, and
     *   Doze may hold it past the moment it was asked for. Dropping the lesson once its moment arrived
     *   would cancel that waiting alarm, and a reminder the tutor was promised would silently never
     *   arrive. So the plan says the lesson is owed, and the adapter leaves its alarm exactly where it
     *   is — the discipline `Digests.stillOwed` states for the same reason on the other scheduler.
     *
     * A lesson that has *ended* leaves the plan altogether, which is what finally lets its alarm go: at
     * that point [shouldPost] would refuse to post it anyway.
     *
     * A **group lesson gets one reminder, not one per member** ([SessionRules.sessions] collapses the
     * members' classes into a single session, and the plan points at its first class): the tutor is
     * being reminded of one lesson they will teach, so *n* identical notifications would be noise.
     */
    fun plan(
        now: Instant,
        classes: List<TutoringClass>,
        lead: Duration = DEFAULT_LEAD,
        horizon: Duration = planningHorizon(lead),
    ): List<Plan> {
        val horizonEnd = now.plus(horizon)
        val owed = classes.filter { klass ->
            val phase = LessonPhase.of(klass, now)
            (phase == LessonPhase.UPCOMING || phase.isRunning) && !(klass.start > horizonEnd)
        }
        return SessionRules.sessions(owed).map { session ->
            val fireAt = session.start.minus(lead)
            Plan(session.first.id, fireAt, due = !(fireAt > now))
        }
    }

    /**
     * Whether a reminder planned for [klass] should still be posted now that it has come due.
     *
     * A reminder is scheduled ahead of time, so by the time it fires the lesson may have been
     * cancelled, completed, or moved — into the past, or far enough forward that this firing is
     * simply early. Only a lesson that is still going to happen — or is happening, since a reminder delayed
     * past the start is still worth seeing before the end — earns the notification, and only while
     * reminders are switched on ([notificationsEnabled] — opt-out, on by default).
     */
    fun shouldPost(
        klass: TutoringClass,
        notificationsEnabled: Boolean,
        now: Instant,
        lead: Duration = DEFAULT_LEAD,
    ): Boolean =
        notificationsEnabled && klass.start.minus(lead) <= now && when (LessonPhase.of(klass, now)) {
            LessonPhase.UPCOMING, LessonPhase.IN_PROGRESS -> true
            LessonPhase.OVERDUE, LessonPhase.SETTLED -> false
        }

    /**
     * The name a reminder is announced by: the **group's** when the lesson belongs to one, otherwise
     * the student's. A group lesson earns one reminder for the several classes it is made of (see
     * [plan]), so naming whichever member's class the plan happened to point at would tell the tutor
     * about one student when several are coming.
     *
     * Blank when neither name is known — the notification then reads as time and subject only, which
     * is still useful, rather than not being posted at all.
     *
     * The rule itself is [Captions.title]'s, so a reminder names a lesson the way the schedule does.
     */
    fun announcedBy(groupName: String?, studentName: String?): String = Captions.title(groupName, studentName)
}
