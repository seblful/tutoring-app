package com.seblful.tutoringapp.calendar

import com.seblful.tutoringapp.domain.model.TutoringClass

/**
 * Mirrors classes into the device calendar (which the OS syncs onward to the user's Google
 * account). A port, so the calendar backend can be swapped — e.g. for the Google Calendar
 * REST API — without touching the rest of the app. Every method is best-effort: when the
 * calendar isn't available it no-ops, so the local database always remains the source of truth.
 */
interface CalendarSync {

    /** Whether events can currently be written (permission granted and a writable calendar exists). */
    suspend fun isAvailable(): Boolean

    /**
     * Creates or updates the event mirroring [klass] (reusing [TutoringClass.calendarEventId] if
     * set) and returns the event id to persist, or null if the calendar is unavailable.
     */
    suspend fun upsert(klass: TutoringClass, title: String): String?

    /** Removes the mirrored event if it still exists. */
    suspend fun delete(eventId: String)
}
