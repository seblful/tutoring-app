package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.TutoringClass
import java.time.Instant
import java.time.ZoneId

/** Generates the occurrences of a weekly recurring class. */
object Recurrence {

    /**
     * Builds [weeks] occurrences of [first], one per week, preserving the wall-clock time
     * in [zone] (so a class stays at e.g. 17:00 local even across a DST change). Every
     * occurrence shares [groupId].
     */
    fun weekly(first: TutoringClass, zone: ZoneId, weeks: Int, groupId: Long): List<TutoringClass> {
        require(weeks >= 1) { "weeks must be >= 1" }
        val firstLocal = first.start.atZone(zone)
        return (0 until weeks).map { w ->
            val start: Instant = firstLocal.plusWeeks(w.toLong()).toInstant()
            first.copy(start = start, recurringGroupId = groupId)
        }
    }
}
