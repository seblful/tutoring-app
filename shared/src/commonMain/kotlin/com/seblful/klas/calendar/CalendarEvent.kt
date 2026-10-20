package com.seblful.klas.calendar

import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.TutoringClass

/** How a class is rendered as a calendar event. Owns the event's display shape. */
object CalendarEvent {

    /** vent title for a class, e.g. "Иван — Математика" (omits blank parts). */
    fun title(studentName: String, subjectName: String, groupName: String? = null): String {
        val base = listOf(studentName, subjectName).filter { it.isNotBlank() }.joinToString(" — ")
        return if (groupName.isNullOrBlank()) base else "$base ($groupName)"
    }

    /** This student's current assignments from the lesson's attached [homework], oldest first. */
    fun description(klass: TutoringClass, homework: List<Homework>): String =
        homework
            .filter { !it.isDone && it.assignee.takes(klass) }
            .sortedBy { it.givenAt }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}
