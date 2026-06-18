package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.TutoringClass
import java.time.Duration
import java.time.Instant

/** Decides which upcoming classes deserve a reminder notification and when it should fire. */
object Reminders {

    /** A planned reminder: which class, and how long after [now] it should fire. */
    data class Plan(val classId: Long, val delayMillis: Long)

    val DEFAULT_LEAD: Duration = Duration.ofMinutes(60)
    val DEFAULT_HORIZON: Duration = Duration.ofDays(7)

    /**
     * Plans a reminder for every SCHEDULED class starting after [now] and within [horizon].
     * Each fires [lead] before the class start; if that moment has already arrived (the class
     * is nearer than [lead]) it fires immediately (delay 0). Past, cancelled, completed and
     * missed classes are skipped — only a class that will still happen earns a reminder.
     */
    fun plan(
        now: Instant,
        classes: List<TutoringClass>,
        lead: Duration = DEFAULT_LEAD,
        horizon: Duration = DEFAULT_HORIZON,
    ): List<Plan> {
        val horizonEnd = now.plus(horizon)
        return classes
            .filter { it.status == ClassStatus.SCHEDULED }
            .filter { it.start.isAfter(now) && !it.start.isAfter(horizonEnd) }
            .map { c ->
                val fireAt = c.start.minus(lead)
                val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(0)
                Plan(c.id, delay)
            }
    }
}
