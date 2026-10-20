package com.seblful.klas.calendar

/** How a class is rendered as a calendar event. Owns the event's display shape. */
object CalendarEvent {

    /** vent title for a class, e.g. "Иван — Математика" (omits blank parts). */
    fun title(studentName: String, subjectName: String, groupName: String? = null): String {
        val base = listOf(studentName, subjectName).filter { it.isNotBlank() }.joinToString(" — ")
        return if (groupName.isNullOrBlank()) base else "$base ($groupName)"
    }

    /**
     * The event's description: **always empty.** A lesson carries no text of the tutor's any more — the
     * note is gone, and homework is attached to a lesson by rule rather than stored on one (`domain/
     * HomeworkRules`). v7 wrote the note here and v8 the homework, so an event mirrored by either still
     * holds it until its lesson is next mirrored, and writing this is what clears it.
     */
    const val DESCRIPTION: String = ""
}
