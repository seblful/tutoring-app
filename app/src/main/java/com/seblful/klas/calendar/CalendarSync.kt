package com.seblful.klas.calendar

import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Reminders

/**
 * The student an event is for, as far as the calendar is concerned: the address that identifies
 * them and whether they should currently be invited to the event.
 *
 * Both halves travel together on purpose. "Don't invite" is not the same as "no address": the
 * former still names the attendee the app added last time, which is what lets turning invites off
 * **withdraw** the existing invitations instead of merely skipping the next ones.
 */
data class Guest(val email: String, val invited: Boolean)

/**
 * Mirrors classes into the device calendar (which the OS syncs onward to the user's Google
 * account). A port, so the calendar backend can be swapped — e.g. for the Google Calendar
 * REST API — without touching the rest of the app. Every method is best-effort: when the
 * calendar isn't available it no-ops, so the local database always remains the source of truth.
 */
interface CalendarSync {

    /**
     * Creates or updates the event mirroring [klass] (reusing [TutoringClass.calendarEventId] if
     * set) and returns the event id to persist, or null if the calendar is unavailable.
     *
     * @param guest the student's address and whether to invite them — an invited guest is added as
     *   an attendee so the calendar account emails them, an uninvited one is removed from the event
     *   if the app had added them. Null when the student has no email, leaving attendees untouched.
     * @param reminderLeadMinutes how long before the class the email reminder fires.
     */
    suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guest: Guest? = null,
        reminderLeadMinutes: Int = Reminders.DEFAULT_LEAD_MINUTES,
    ): String?

    /**
     * Writes [klass]'s current fields to the event it is already mirrored by — and does **nothing at
     * all** if there is no such event.
     *
     * The narrow half of [upsert], for a lesson that has already been taught (`CalendarSyncPolicy`
     * decides which is which). Three things it deliberately cannot do, each of which would be wrong
     * about a past hour:
     *
     * - **create an event**, so turning sync on never backfills the tutor's history into their
     *   calendar, and an event they deleted from it themselves is not put back by an edit;
     * - **return an id**, because no id can have changed — which is what lets the caller skip a write
     *   back to `classes.calendarEventId` rather than guard one;
     * - **touch attendees or reminders**, since re-adding the student to a lesson that is over would
     *   email them an invitation to an hour that has passed.
     *
     * Best-effort like everything else here: a missing permission, a missing calendar or an event the
     * tutor has since deleted are all silent no-ops.
     */
    suspend fun refresh(klass: TutoringClass, title: String)

    /** Removes the mirrored event if it still exists. */
    suspend fun delete(eventId: String)
}
