package com.seblful.klas.calendar

import com.seblful.klas.domain.model.TutoringClass

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

    /**
     * The event's description: **the homework, and never the note.**
     *
     * The description used to be the note, which was one field for what was covered and what was set —
     * and an invited student is an attendee of that same event, so whatever the tutor wrote about the
     * lesson reached the student's calendar with it. The two are separate fields now because they are
     * written for different readers: the homework is for the student, the note is the tutor's own.
     *
     * Verbatim, with no label: it is the tutor's text, and the event already says which lesson it is.
     * Blank when nothing was set, which is what clears the description once the homework is deleted.
     */
    fun description(klass: TutoringClass): String = klass.homework
}
