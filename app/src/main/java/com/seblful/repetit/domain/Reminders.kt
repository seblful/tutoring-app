package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import java.time.Duration
import java.time.Instant

/** Decides which upcoming classes deserve a reminder notification and when it should fire. */
object Reminders {

    /** A planned reminder: which class, and how long after [now] it should fire. */
    data class Plan(val classId: Long, val delayMillis: Long)

    val DEFAULT_LEAD: Duration = Duration.ofMinutes(60)
    val DEFAULT_HORIZON: Duration = Duration.ofDays(7)

    /**
     * Plans a reminder for every SCHEDULED lesson starting after [now] and within [horizon].
     * Each fires [lead] before the lesson start; if that moment has already arrived (the lesson
     * is nearer than [lead]) it fires immediately (delay 0). Past, cancelled and completed
     * classes are skipped — only a class that will still happen earns a reminder.
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
        return GroupRules.sessions(upcoming).map { session ->
            val fireAt = session.start.minus(lead)
            val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(0)
            Plan(session.first.id, delay)
        }
    }
}
