package com.seblful.repetit.calendar

/** How a class is rendered as a calendar event. Owns the event's display shape. */
object CalendarEvent {

    /**
     * Event title for a class, e.g. "Иван — Математика" (omits blank parts).
     *
     * A class booked as part of a group appends the group's name — "Иван — Математика (11 «А»)" —
     * because a group lesson mirrors as one event per member, and the group name is what tells those
     * overlapping events apart from unrelated back-to-back classes.
     */
    fun title(studentName: String, subjectName: String, groupName: String? = null): String {
        val base = listOf(studentName, subjectName).filter { it.isNotBlank() }.joinToString(" — ")
        return if (groupName.isNullOrBlank()) base else "$base ($groupName)"
    }
}
