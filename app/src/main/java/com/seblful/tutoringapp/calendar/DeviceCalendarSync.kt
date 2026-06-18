package com.seblful.tutoringapp.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.TutoringClass
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
 * WRITE_CALENDAR runtime permission. All operations are best-effort and never throw to callers.
 */
@Singleton
class DeviceCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarSync {

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        hasPermission() && writableCalendarId() != null
    }

    override suspend fun upsert(klass: TutoringClass, title: String): String? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val calendarId = writableCalendarId() ?: return@withContext null
        val values = eventValues(klass, title, calendarId)

        val existingId = klass.calendarEventId?.toLongOrNull()
        if (existingId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val updated = runCatching { context.contentResolver.update(uri, values, null, null) }.getOrDefault(0)
            // The user may have deleted the event from their calendar — recreate it if so.
            if (updated > 0) existingId.toString() else insert(values)
        } else {
            insert(values)
        }
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
        put(CalendarContract.Events.EVENT_LOCATION, context.getString(modeLabelRes(klass.mode)))
    }

    private fun modeLabelRes(mode: ClassMode): Int = when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }

    /** The primary writable calendar's id (preferring the account's primary), or null if none. */
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_OWNER.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, args,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
