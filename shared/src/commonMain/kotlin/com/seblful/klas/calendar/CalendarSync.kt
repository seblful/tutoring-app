package com.seblful.klas.calendar

import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Reminders

/**
 * The student an event is for, as far as the calendar is concerned: the address that identifies
 * them and whether they should currently be invited to the event.
 */
data class Guest(
    val email: String,
    val invited: Boolean,
    /** The pre-edit address lets a device adopt invitations made before it tracked their ownership. */
    val previousEmail: String? = null,
)

/**
 * Mirrors classes into the device calendar (which the OS syncs onward to the user's Google
 * account). A port, so the calendar backend can be swapped — e.g. for the Google Calendar
 * REST API — without touching the rest of the app.
 */
interface CalendarSync {

    /**
     * Creates or updates the event mirroring [klass] (reusing [TutoringClass.calendarEventId] if
     * set) and returns the event id to persist, or null if the calendar is unavailable.
     *
     * @param guest the student's address and whether to invite them — an invited guest is added as
     * an attendee so the calendar account emails them, an uninvited one is removed from the event
     * if the app had added them. Null withdraws the app's managed invitation; unrelated attendees stay.
     * @param reminderLeadMinutes how long before the class the email reminder fires.
     * @param description homework for this student's lesson, included in the calendar invitation.
     */
    suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guest: Guest? = null,
        reminderLeadMinutes: Int = Reminders.DEFAULT_LEAD_MINUTES,
        description: String,
    ): String?

    /**
     * Writes [klass]'s current fields to the event it is already mirrored by — and does **nothing at
     * all** if there is no such event.
     *
     * @param description as for [upsert]; empty clears homework the event no longer carries.
     */
    suspend fun refresh(klass: TutoringClass, title: String, description: String)

    /** Removes the mirrored event if it still exists. */
    suspend fun delete(eventId: String)
}
