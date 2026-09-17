package com.seblful.klas.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.inAppLanguage
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
    /**
     * The app's one answer to "may we touch the calendar", rather than a private copy.
     *
     * This class used to ask `ContextCompat.checkSelfPermission` itself, in two private methods, while
     * [CalendarPermission] sat in the same package answering the same question for the settings screen.
     * Two consequences: the definition could drift, and — because the check and the writing were joined
     * only inside this untestable class — nothing above could observe that a revoked grant stops the
     * mirror. `CalendarMirror` takes a [CalendarSync], so with the check hidden in here there was no seam
     * at which "the tutor revoked the permission while the app was away" could be set up at all.
     */
    private val permission: CalendarPermission,
) : CalendarSync {

    override suspend fun upsert(klass: TutoringClass, title: String, guest: Guest?, reminderLeadMinutes: Int): String? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val calendarId = writableCalendarId() ?: return@withContext null
        val values = eventValues(klass, title, calendarId)

        val existingId = klass.calendarEventId?.toLongOrNull()
        // The user may have deleted the event from their calendar — recreate it if so. A lesson still
        // to come is the only kind this happens for; `refresh` is the arm that must not recreate.
        val eventId = (existingId?.takeIf { write(it, values) } ?: insert(values)?.toLongOrNull())
            ?: return@withContext null

        // Email reminder before the class, sent by the calendar account. Idempotent: drop and
        // re-add so an updated time/lead doesn't accumulate stale reminders.
        upsertEmailReminder(eventId, reminderLeadMinutes)
        // Invite the student so the account emails them about the class (and its reminder) — or
        // withdraw that invitation if the tutor has since turned invites off. The guard is a read
        // optimisation and not the rule: `guestAction` answers `None` for a missing address anyway, so
        // this only decides whether the provider is worth asking.
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
     * Brings the event's attendees in line with [guest]. Best-effort.
     *
     * What that comes to — add, withdraw, or leave alone — is [guestAction], including the two properties
     * worth having a test for: only ever this one address, and idempotent in both directions.
     */
    private fun syncGuest(eventId: Long, guest: Guest) {
        runCatching {
            when (val action = guestAction(attendees(eventId), guest)) {
                GuestAction.None -> Unit
                is GuestAction.Add -> addGuest(eventId, action.email)
                is GuestAction.Remove -> removeGuest(eventId, action.email)
            }
        }
    }

    /** Every address currently on the event, the tutor's own guests included. */
    private fun attendees(eventId: Long): List<String> =
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL),
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        }.orEmpty()

    /** Adds [email] as an attendee. Whether it was needed is [guestAction]'s answer, not this one's. */
    private fun addGuest(eventId: Long, email: String) {
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

    /**
     * The event, brought in line with the lesson — or nothing, if there is no event.
     *
     * Everything this does *not* do is the point, and the reasons are on [CalendarSync.refresh]: no
     * insert, no id back, no attendees, no reminders. What is left is one [ContentValues] written over
     * one row, which is also why it shares [eventValues] with [upsert] — a past lesson and a future one
     * put the same fields on an event, and two copies of that list would be two events that disagree
     * about what a lesson looks like.
     */
    override suspend fun refresh(klass: TutoringClass, title: String) {
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext
            val eventId = klass.calendarEventId?.toLongOrNull() ?: return@withContext
            val calendarId = writableCalendarId() ?: return@withContext
            write(eventId, eventValues(klass, title, calendarId))
        }
    }

    /** One update over one event row; false when the provider wrote nothing — the usual cause being
     *  that the tutor deleted the event. What that *means* is the caller's, which is the difference
     *  between [upsert] (recreate it) and [refresh] (let it be). */
    private fun write(eventId: Long, values: ContentValues): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching { context.contentResolver.update(uri, values, null, null) }.getOrDefault(0) > 0
    }

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
        // What was covered and what was set for home, verbatim — the note is already the tutor's own
        // words about the hour, so there is nothing here to assemble or translate (contrast the title,
        // which is three tables' names, and the location, which is one of the app's own).
        //
        // **Written every time, empty string included.** Clearing a note has to clear the description:
        // an unconditional put is what makes "the calendar says what the app says" true in both
        // directions, where skipping the blank case would leave last week's homework standing on an
        // event whose note the tutor deliberately emptied.
        put(CalendarContract.Events.DESCRIPTION, klass.notes)
        // The one **word** this class writes, so it is the one thing read in the tutor's chosen language
        // rather than off the application context (which is pinned to the phone's — see `KlasApp`). An event
        // keeps the language it was mirrored in until something re-mirrors it, which is why changing the
        // language owes a resync: `SettingsChange.calendarWork`.
        put(CalendarContract.Events.EVENT_LOCATION, context.inAppLanguage().getString(modeLabelRes(klass.mode)))
        // Mark that this event carries reminders/guests, so the provider keeps the rows we add.
        put(CalendarContract.Events.HAS_ALARM, 1)
        put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1)
    }

    private fun modeLabelRes(mode: ClassMode): Int = when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }

    /**
     * The id of a calendar we can add events to, or null if none exists.
     *
     * The **rule** — which levels may be written to, and primary before visible before more-privileged —
     * is [CalendarChoice.preferred], where a test can reach it. What is left here is the read: every
     * calendar the phone has, unfiltered and unordered, since a handful of rows is not worth a `WHERE` at
     * the cost of putting the decision back in a `ContentResolver` call.
     */
    private fun writableCalendarId(): Long? = runCatching {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CalendarRow(
                                id = cursor.getLong(0),
                                isPrimary = cursor.getInt(1) != 0,
                                visible = cursor.getInt(2) != 0,
                                accessLevel = cursor.getInt(3),
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }.getOrNull()?.let(CalendarChoice::preferred)

    // Reading the calendar list needs READ_CALENDAR; writing events needs WRITE_CALENDAR. Both are
    // required, and both must be requested — granting one does not imply the other. Asked of the port, so
    // this class and the settings row cannot disagree about what "granted" means.
    private fun hasPermission(): Boolean = permission.isGranted()

    private companion object {
        /** One attendee of one event, by address — what a withdrawal targets. */
        const val ATTENDEE_SELECTION =
            "${CalendarContract.Attendees.EVENT_ID} = ? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?"
    }
}
