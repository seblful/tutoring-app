package com.seblful.klas.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.inAppLanguage
import com.seblful.klas.format.labelRes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * Writes class events to the device calendar via [CalendarContract]. Events land in the user's
 * primary calendar (typically their Google account), and Android's own sync adapter pushes them
 * to Google Calendar — so there is no backend, OAuth, or API key to configure here, only the
 * READ_CALENDAR + WRITE_CALENDAR runtime permissions (READ to locate a writable calendar, WRITE
 * to add events).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DeviceCalendarSync @Inject constructor(
    @param:ApplicationContext private val context: Context,

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

    private fun syncGuest(eventId: Long, guest: Guest) {
        runCatching {
            when (val action = guestAction(attendees(eventId), guest)) {
                GuestAction.None -> Unit
                is GuestAction.Add -> addGuest(eventId, action.email)
                is GuestAction.Remove -> removeGuest(eventId, action.email)
            }
        }
    }

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

    private fun removeGuest(eventId: Long, email: String) {
        context.contentResolver.delete(
            CalendarContract.Attendees.CONTENT_URI,
            ATTENDEE_SELECTION,
            arrayOf(eventId.toString(), email),
        )
    }

    /** he event, brought in line with the lesson — or nothing, if there is no event. */
    override suspend fun refresh(klass: TutoringClass, title: String) {
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext
            val eventId = klass.calendarEventId?.toLongOrNull() ?: return@withContext
            val calendarId = writableCalendarId() ?: return@withContext
            write(eventId, eventValues(klass, title, calendarId))
        }
    }

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
        put(CalendarContract.Events.DTSTART, klass.start.toEpochMilliseconds())
        put(CalendarContract.Events.DTEND, klass.end.toEpochMilliseconds())
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        // **Written every time, although it is always empty** — see `CalendarEvent.DESCRIPTION`. Leaving
        // it out would leave the note or the homework an older build wrote standing on the event, in
        // front of an invited student, with nothing in the app to say it is there.
        put(CalendarContract.Events.DESCRIPTION, CalendarEvent.DESCRIPTION)
        // The one **word** this class writes, so it is the one thing read in the tutor's chosen language
        // rather than off the application context (which is pinned to the phone's — see `KlasApp`). An event
        // keeps the language it was mirrored in until something re-mirrors it, which is why changing the
        // language owes a resync: `SettingsChange.calendarWork`.
        put(CalendarContract.Events.EVENT_LOCATION, context.inAppLanguage().getString(klass.mode.labelRes()))
        // Mark that this event carries reminders/guests, so the provider keeps the rows we add.
        put(CalendarContract.Events.HAS_ALARM, 1)
        put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1)
    }

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
