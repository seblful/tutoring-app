package com.seblful.klas.calendar

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.CalendarContract
import com.seblful.klas.data.settings.SettingsChange
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.format.inLanguage
import com.seblful.klas.format.labelRes
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val displayLocale: DisplayLocale,
) : CalendarSync {

    private val mutationLock = Mutex()
    private val guests = CalendarGuestLedger(context)

    override suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guest: Guest?,
        reminderLeadMinutes: Int,
        description: String,
    ): String? =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                var createdId: String? = null
                runCatchingCancellable {
                    val previous = klass.calendarEventId?.toLongOrNull()
                    previous?.let { guests.rememberPrevious(it, guest?.previousEmail) }
                    if (!hasPermission()) return@runCatchingCancellable null
                    val calendarId = writableCalendarId() ?: return@runCatchingCancellable null
                    val values = eventValues(klass, title, description, calendarId)
                    val updated = previous?.let { id ->
                        // An obsolete recipient must go before new titles or homework reach the event.
                        val plan = prepareGuests(id, guest)
                        if (write(id, values)) id to plan else null
                    }
                    val (eventId, plan) = updated ?: run {
                        previous?.let(guests::forget)
                        val id = insert(values)?.toLongOrNull() ?: return@runCatchingCancellable null
                        createdId = id.toString()
                        guests.write(id, emptySet())
                        id to guestPlan(emptySet(), emptySet(), guest)
                    }
                    setEmailReminder(eventId, reminderLeadMinutes, replacing = updated != null)
                    plan.add?.let { email ->
                        guests.write(eventId, guests.managed(eventId).orEmpty() + email)
                        addGuest(eventId, email)
                    }
                    eventId.toString()
                }.getOrElse {
                    // Once an event exists its binding must survive a failed guest or ownership write.
                    createdId
                }
            }
        }

    /**
     * Sets the event's one email reminder. An existing event's is dropped and re-added in one batch, so an
     * updated time or lead doesn't accumulate stale reminders; a new event has none to drop.
     */
    private fun setEmailReminder(eventId: Long, leadMinutes: Int, replacing: Boolean) {
        val add = ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
            .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_EMAIL)
            .withValue(CalendarContract.Reminders.MINUTES, leadMinutes)
            .build()
        val drop = ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
            .withSelection(
                "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ?",
                arrayOf(eventId.toString(), CalendarContract.Reminders.METHOD_EMAIL.toString()),
            )
            .build()
        val operations = if (replacing) arrayListOf(drop, add) else arrayListOf(add)
        runCatching { context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations) }
    }

    private fun prepareGuests(eventId: Long, guest: Guest?): GuestPlan {
        val plan = guestPlan(attendees(eventId), guests.managed(eventId), guest)
        guests.write(eventId, plan.managed, retiring = plan.remove)
        retireGuests(eventId)
        return plan
    }

    private fun retireGuests(eventId: Long) {
        val retiring = guests.retiring(eventId)
        if (retiring.isEmpty()) return
        retiring.forEach { removeGuest(eventId, it) }
        check(attendees(eventId).none { it in retiring }) { "Calendar invitation was not withdrawn" }
        guests.write(eventId, guests.managed(eventId).orEmpty() - retiring)
    }

    private fun attendees(eventId: Long): Set<String> =
        checkNotNull(
            context.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL),
                "${CalendarContract.Attendees.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null,
            ),
        ) { "Calendar attendees unavailable" }.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        }

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
        checkNotNull(context.contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, values)) {
            "Calendar invitation was not added"
        }
    }

    private fun removeGuest(eventId: Long, email: String) {
        context.contentResolver.delete(
            CalendarContract.Attendees.CONTENT_URI,
            ATTENDEE_SELECTION,
            arrayOf(eventId.toString(), email),
        )
    }

    /** The event, brought in line with the lesson — or nothing, if there is no event. */
    override suspend fun refresh(klass: TutoringClass, title: String, description: String) {
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                runCatchingCancellable {
                    if (!hasPermission()) return@runCatchingCancellable
                    val eventId = klass.calendarEventId?.toLongOrNull() ?: return@runCatchingCancellable
                    val calendarId = writableCalendarId() ?: return@runCatchingCancellable
                    retireGuests(eventId)
                    write(eventId, eventValues(klass, title, description, calendarId))
                }
            }
        }
    }

    private fun write(eventId: Long, values: ContentValues): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return context.contentResolver.update(uri, values, null, null) > 0
    }

    override suspend fun delete(eventId: String) {
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                runCatchingCancellable {
                    if (!hasPermission()) return@runCatchingCancellable
                    val id = eventId.toLongOrNull() ?: return@runCatchingCancellable
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                    context.contentResolver.delete(uri, null, null)
                    guests.forget(id)
                }
            }
        }
    }

    private fun insert(values: ContentValues): String? {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return uri.lastPathSegment
    }

    private fun eventValues(
        klass: TutoringClass,
        title: String,
        description: String,
        calendarId: Long,
    ) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, klass.start.toEpochMilliseconds())
        put(CalendarContract.Events.DTEND, klass.end.toEpochMilliseconds())
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        // Write even an empty description to clear homework that was removed or marked done.
        put(CalendarContract.Events.DESCRIPTION, description)
        // The one **word** this class writes, so it is the one thing read in the tutor's chosen language
        // rather than off the application context (which is pinned to the phone's — see `KlasApp`). An event
        // keeps the language it was mirrored in until something re-mirrors it, which is why changing the
        // language owes a resync: `SettingsChange.calendarWork`.
        put(
            CalendarContract.Events.EVENT_LOCATION,
            context.inLanguage(displayLocale.current).getString(klass.mode.labelRes()),
        )
        // Mark that this event carries reminders/guests, so the provider keeps the rows we add.
        put(CalendarContract.Events.HAS_ALARM, 1)
        put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1)
    }

    /** When [cachedCalendarId] was read, by [SystemClock.elapsedRealtime]. */
    @Volatile
    private var cachedCalendarAt = 0L

    @Volatile
    private var cachedCalendarId: Long? = null

    /**
     * The calendar events are written to — read once per mirroring pass rather than once per event, and
     * re-read once [CALENDAR_TTL_MILLIS] has passed, so a calendar the tutor adds or removes is followed.
     */
    private fun writableCalendarId(): Long? {
        val now = SystemClock.elapsedRealtime()
        cachedCalendarId?.let { if (now - cachedCalendarAt < CALENDAR_TTL_MILLIS) return it }
        return queryWritableCalendarId().also {
            cachedCalendarId = it
            cachedCalendarAt = now
        }
    }

    private fun queryWritableCalendarId(): Long? = runCatching {
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
        const val CALENDAR_TTL_MILLIS = 30_000L

        /** One attendee of one event, by address — what a withdrawal targets. */
        const val ATTENDEE_SELECTION =
            "${CalendarContract.Attendees.EVENT_ID} = ? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?"
    }
}
