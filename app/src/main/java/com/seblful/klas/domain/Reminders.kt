package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.TutoringClass
import java.time.Duration
import java.time.Instant

/** Decides which upcoming classes deserve a reminder notification and when it should fire. */
object Reminders {

    /**
     * A planned reminder: which class, and the **instant** it fires.
     *
     * An absolute instant, not a delay. The delay was WorkManager's shape (`setInitialDelay`), kept
     * after reminders became alarms — and it made the only adapter reconstruct the firing time
     * against a second clock read of its own, some moments after the one the plan was derived from.
     * When a reminder fires is this module's decision; an adapter arms what it is handed.
     */
    data class Plan(val classId: Long, val fireAt: Instant)

    val DEFAULT_LEAD: Duration = Duration.ofMinutes(60)
    val DEFAULT_HORIZON: Duration = Duration.ofDays(7)

    /**
     * Plans a reminder for every SCHEDULED lesson starting after [now] and within [horizon].
     * Each fires [lead] before the lesson start. Past, cancelled and completed classes are
     * skipped — only a class that will still happen earns a reminder.
     *
     * A lesson whose lead moment has **already passed** earns no reminder: the whole plan is
     * re-derived from scratch on every class change, every app start and every maintenance tick,
     * so firing "late" reminders immediately would announce the same imminent lesson again on each
     * of those — the tutor books a class for the next hour and is told about it right away, then
     * told again the next time anything changes. A reminder is a warning *ahead* of a lesson; once
     * that moment is behind us there is nothing left to warn about, and the tutor who just booked
     * the lesson is looking at it anyway.
     *
     * A **group lesson gets one reminder, not one per member** ([GroupRules.sessions] collapses the
     * members' classes into a single session, and the plan points at its first class): the tutor is
     * being reminded of one lesson they will teach, so *n* identical notifications would be noise.
     */
    fun plan(
        now: Instant,
        classes: List<TutoringClass>,
        lead: Duration = DEFAULT_LEAD,
        horizon: Duration = DEFAULT_HORIZON,
    ): List<Plan> {
        val horizonEnd = now.plus(horizon)
        val upcoming = classes
            .filter { it.status == ClassStatus.SCHEDULED }
            .filter { it.start.isAfter(now) && !it.start.isAfter(horizonEnd) }
        return GroupRules.sessions(upcoming).mapNotNull { session ->
            val fireAt = session.start.minus(lead)
            if (!fireAt.isAfter(now)) return@mapNotNull null
            Plan(session.first.id, fireAt)
        }
    }

    /**
     * Whether a reminder planned for [klass] should still be posted now that it has come due.
     *
     * A reminder is scheduled ahead of time, so by the time it fires the lesson may have been
     * cancelled, completed, or moved — into the past, or far enough forward that this firing is
     * simply early. Only a lesson that is still going to happen earns the notification, and only
     * while reminders are switched on ([notificationsEnabled] — opt-out, on by default).
     */
    fun shouldPost(klass: TutoringClass, notificationsEnabled: Boolean, now: Instant): Boolean =
        notificationsEnabled &&
            klass.status == ClassStatus.SCHEDULED &&
            klass.end.isAfter(now)

    /**
     * The name a reminder is announced by: the **group's** when the lesson belongs to one, otherwise
     * the student's. A group lesson earns one reminder for the several classes it is made of (see
     * [plan]), so naming whichever member's class the plan happened to point at would tell the tutor
     * about one student when several are coming.
     *
     * Blank when neither name is known — the notification then reads as time and subject only, which
     * is still useful, rather than not being posted at all.
     */
    fun announcedBy(groupName: String?, studentName: String?): String =
        groupName?.takeIf { it.isNotBlank() } ?: studentName.orEmpty()
}
