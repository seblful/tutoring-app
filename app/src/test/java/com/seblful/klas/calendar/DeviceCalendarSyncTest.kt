package com.seblful.klas.calendar

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DeviceCalendarSyncTest {

    @get:Rule(order = 0)
    val env = TestEnv()

    private lateinit var provider: FakeCalendarProvider
    private lateinit var sync: DeviceCalendarSync

    @Before
    fun setUp() {
        provider = Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
        sync = DeviceCalendarSync(
            ApplicationProvider.getApplicationContext(),
            FakeCalendarPermission(),
            DisplayLocale(env.settingsRepo, DevicePreferences(listOf("ru-BY"), listOf("BYN"))),
        )
    }

    private fun lesson(id: Long, eventId: String? = null) = TutoringClass(
        id = id,
        studentId = 1,
        subjectId = 1,
        start = Instant.parse("2026-06-20T10:00:00Z"),
        durationMinutes = 60,
        calendarEventId = eventId,
    )

    private suspend fun upsert(klass: TutoringClass, lead: Int = 60): String =
        sync.upsert(klass, title = "Аня — Алгебра", guest = null, reminderLeadMinutes = lead, description = "")!!

    @Test
    fun `a new event gets one email reminder`() = runTest {
        val eventId = upsert(lesson(1), lead = 30)

        assertEquals(setOf(eventId.toLong()), provider.events.keys)
        assertEquals(listOf(eventId.toLong() to 30), provider.reminderLeads())
    }

    @Test
    fun `updating an event replaces its reminder instead of adding another`() = runTest {
        val eventId = upsert(lesson(1), lead = 30)

        assertEquals(eventId, upsert(lesson(1, eventId), lead = 1440))

        assertEquals(listOf(eventId.toLong() to 1440), provider.reminderLeads())
    }

    @Test
    fun `a pass reads the calendar list once rather than per event`() = runTest {
        repeat(3) { upsert(lesson(it.toLong() + 1)) }

        assertEquals(1, provider.calendarQueries)
    }

    @Test
    fun `an event deleted from the calendar is created again`() = runTest {
        val eventId = upsert(lesson(1))
        provider.events.clear()

        val again = upsert(lesson(1, eventId))

        assertNotEquals(eventId, again)
        assertEquals(setOf(again.toLong()), provider.events.keys)
    }
}

/** The calendar provider's tables, as far as the adapter writes them. */
class FakeCalendarProvider : ContentProvider() {
    val events = mutableMapOf<Long, ContentValues>()
    private val reminders = mutableListOf<ContentValues>()
    var calendarQueries = 0
        private set
    private var nextId = 1L

    fun reminderLeads(): List<Pair<Long, Int>> = reminders.map {
        it.getAsLong(CalendarContract.Reminders.EVENT_ID) to it.getAsInteger(CalendarContract.Reminders.MINUTES)
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: emptyArray()).apply {
        if (uri.pathSegments.first() == "calendars") {
            calendarQueries++
            addRow(arrayOf<Any>(7L, 1, 1, CalendarContract.Calendars.CAL_ACCESS_OWNER))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = when (uri.pathSegments.first()) {
        "events" -> nextId++.also { events[it] = ContentValues(values) }.let { ContentUris.withAppendedId(uri, it) }
        "reminders" -> {
            reminders += ContentValues(values)
            ContentUris.withAppendedId(uri, reminders.size.toLong())
        }
        else -> null
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = ContentUris.parseId(uri)
        val known = uri.pathSegments.first() == "events" && id in events
        if (known) events[id] = ContentValues(values)
        return if (known) 1 else 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        when (uri.pathSegments.first()) {
            "events" -> if (events.remove(ContentUris.parseId(uri)) != null) 1 else 0
            "reminders" -> {
                val eventId = selectionArgs!!.first().toLong()
                val before = reminders.size
                reminders.removeAll { it.getAsLong(CalendarContract.Reminders.EVENT_ID) == eventId }
                before - reminders.size
            }
            else -> 0
        }

    override fun getType(uri: Uri): String? = null
}
