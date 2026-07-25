package com.seblful.repetit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.seblful.repetit.R
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.TutoringClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes class events to the device calendar via [CalendarContract]. Events land in the user's
 * primary calendar (typically their Google account), and Android's own sync adapter pushes them
 * to Google Calendar — so there is no backend, OAuth, or API key to configure here, only the
 * READ_CALENDAR + WRITE_CALENDAR runtime permissions (READ to locate a writable calendar, WRITE
 * to add events). All operations are best-effort and never throw to callers.
 */
@Singleton
class DeviceCalendarSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CalendarSync {

    override suspend fun upsert(klass: TutoringClass, title: String, guest: Guest?, reminderLeadMinutes: Int): String? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val calendarId = writableCalendarId() ?: return@withContext null
        val values = eventValues(klass, title, calendarId)

        val existingId = klass.calendarEventId?.toLongOrNull()
        val eventId = if (existingId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val updated = runCatching { context.contentResolver.update(uri, values, null, null) }.getOrDefault(0)
            // The user may have deleted the event from their calendar — recreate it if so.
            if (updated > 0) existingId else insert(values)?.toLongOrNull()
        } else {
            insert(values)?.toLongOrNull()
        } ?: return@withContext null

        // Email reminder before the class, sent by the calendar account. Idempotent: drop and
        // re-add so an updated time/lead doesn't accumulate stale reminders.
        upsertEmailReminder(eventId, reminderLeadMinutes)
        // Invite the student so the account emails them about the class (and its reminder) — or
        // withdraw that invitation if the tutor has since turned invites off.
        guest?.takeIf { it.email.isNotBlank() }?.let { syncGuest(eventId, it) }

        eventId.toString()
    }

    /** Replaces the event's email reminders with a single one [leadMinutes] before start. */
    private fun upsertEmailReminder(eventId: Long, leadMinutes: Int) {
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ?",
                arrayOf(eventId.toString(), CalendarContract.Reminders.METHOD_EMAIL.toString()),
            )
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_EMAIL)
                put(CalendarContract.Reminders.MINUTES, leadMinutes)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

    /**
     * Brings the event's attendees in line with [guest] — invited means present, uninvited means
     * gone — idempotently in both directions, so re-syncing after the tutor flips the setting
     * either sends the invitation or withdraws it. Only ever touches that one address, leaving
     * guests the tutor added themselves in their calendar alone. Best-effort.
     */
    private fun syncGuest(eventId: Long, guest: Guest) {
        runCatching {
            if (guest.invited) addGuest(eventId, guest.email) else removeGuest(eventId, guest.email)
        }
    }

    /** Adds [email] as an attendee if not already present. */
    private fun addGuest(eventId: Long, email: String) {
        if (isAttendee(eventId, email)) return
        val values = ContentValues().apply {
            put(CalendarContract.Attendees.EVENT_ID, eventId)
            put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
            put(
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.RELATIONSHIP_ATTENDEE,
            )
            put(
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.TYPE_REQUIRED,
            )
            put(
                CalendarContract.Attendees.ATTENDEE_STATUS,
                CalendarContract.Attendees.ATTENDEE_STATUS_INVITED,
            )
        }
        context.contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, values)
    }

    /** Drops [email] from the event's attendees, withdrawing the invitation the app sent. */
    private fun removeGuest(eventId: Long, email: String) {
        context.contentResolver.delete(
            CalendarContract.Attendees.CONTENT_URI,
            ATTENDEE_SELECTION,
            arrayOf(eventId.toString(), email),
        )
    }

    private fun isAttendee(eventId: Long, email: String): Boolean =
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees._ID),
            ATTENDEE_SELECTION,
            arrayOf(eventId.toString(), email),
            null,
        )?.use { it.count > 0 } ?: false

    override suspend fun delete(eventId: String) {
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext
            val id = eventId.toLongOrNull() ?: return@withContext
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun insert(values: ContentValues): String? {
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }
            .getOrNull() ?: return null
        return uri.lastPathSegment
    }

    private fun eventValues(klass: TutoringClass, title: String, calendarId: Long) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, klass.start.toEpochMilli())
        put(CalendarContract.Events.DTEND, klass.end.toEpochMilli())
        put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        put(CalendarContract.Events.EVENT_LOCATION, context.getString(modeLabelRes(klass.mode)))
        // Mark that this event carries reminders/guests, so the provider keeps the rows we add.
        put(CalendarContract.Events.HAS_ALARM, 1)
        put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1)
    }

    private fun modeLabelRes(mode: ClassMode): Int = when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }

    /**
     * The id of a calendar we can add events to, or null if none exists. Requires only
     * [CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR] — the lowest level that may insert
     * events — because a synced Google account calendar is often reported as EDITOR/CONTRIBUTOR
     * rather than OWNER; demanding OWNER matched nothing on those devices, so every event
     * silently no-opped. Prefers the account's primary, then a visible, then a more-privileged
     * calendar.
     */
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val order = "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
            "${CalendarContract.Calendars.VISIBLE} DESC, " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} DESC"
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, args, order,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    // Reading the calendar list needs READ_CALENDAR; writing events needs WRITE_CALENDAR. Both are
    // required, and both must be requested — granting one does not imply the other.
    private fun hasPermission(): Boolean =
        isGranted(Manifest.permission.READ_CALENDAR) && isGranted(Manifest.permission.WRITE_CALENDAR)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** One attendee of one event, by address — shared so add and remove can't target differently. */
        const val ATTENDEE_SELECTION =
            "${CalendarContract.Attendees.EVENT_ID} = ? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?"
    }
}
