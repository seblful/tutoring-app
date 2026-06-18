package com.seblful.tutoringapp.calendar

/** How a class is rendered as a calendar event. Owns the event's display shape. */
object CalendarEvent {

    /** Event title for a class, e.g. "Иван — Математика" (omits blank parts). */
    fun title(studentName: String, subjectName: String): String =
        listOf(studentName, subjectName).filter { it.isNotBlank() }.joinToString(" — ")
}
