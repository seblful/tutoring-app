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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        sync = calendar()
    }

    private fun calendar(permission: FakeCalendarPermission = FakeCalendarPermission()) = DeviceCalendarSync(
        ApplicationProvider.getApplicationContext(),
        permission,
        DisplayLocale(env.settingsRepo, DevicePreferences(listOf("ru-BY"), listOf("BYN"))),
        env.clock,
        Dispatchers.Unconfined,
    )

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
    fun `an event is written in the clock's zone`() = runTest {
        val eventId = upsert(lesson(1))

        assertEquals(
            env.clock.zone.id,
            provider.events.getValue(eventId.toLong()).getAsString(CalendarContract.Events.EVENT_TIMEZONE),
        )
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

    private suspend fun invite(
        klass: TutoringClass,
        guest: Guest?,
        description: String = "Exercise 1",
    ): String? = sync.upsert(klass, "Аня — Алгебра", guest, description = description)

    private fun legacyEvent(email: String): String {
        val uri = provider.insert(CalendarContract.Events.CONTENT_URI, ContentValues())!!
        val id = ContentUris.parseId(uri)
        provider.addGuest(id, email)
        return id.toString()
    }

    private fun description(eventId: String): String? =
        provider.events.getValue(eventId.toLong()).getAsString(CalendarContract.Events.DESCRIPTION)

    @Test
    fun `correcting an invited address withdraws the old guest and preserves manual guests`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.addGuest(eventId.toLong(), "manual@example.com")

        invite(lesson(1, eventId), Guest("new@example.com", true))

        assertEquals(setOf("new@example.com", "manual@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `clearing an address withdraws the invitation after restarting the adapter`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.addGuest(eventId.toLong(), "manual@example.com")
        sync = calendar()

        invite(lesson(1, eventId), guest = null)

        assertEquals(setOf("manual@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `turning invitations off withdraws the managed guest after restarting the adapter`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        sync = calendar()

        invite(lesson(1, eventId), Guest("old@example.com", false))

        assertEquals(emptySet<String>(), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `a matching guest added manually to a tracked event is never claimed`() = runTest {
        val eventId = invite(lesson(1), guest = null)!!
        provider.addGuest(eventId.toLong(), "manual@example.com")

        invite(lesson(1, eventId), Guest("manual@example.com", true))
        sync = calendar()
        invite(lesson(1, eventId), Guest("manual@example.com", false))

        assertEquals(setOf("manual@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `failed withdrawal withholds new homework and retries the old address after restart`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.failGuestDelete = true

        val failed = invite(lesson(1, eventId), Guest("new@example.com", true), description = "Private exercise")

        assertNull(failed)
        assertEquals("Exercise 1", description(eventId))
        assertEquals(setOf("old@example.com"), provider.guestEmails(eventId.toLong()))

        provider.failGuestDelete = false
        sync = calendar()
        invite(lesson(1, eventId), Guest("latest@example.com", true), description = "Private exercise")

        assertEquals(setOf("latest@example.com"), provider.guestEmails(eventId.toLong()))
        assertEquals("Private exercise", description(eventId))
    }

    @Test
    fun `a failed attendee query leaves existing event content untouched`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.failGuestQuery = true

        val result = invite(lesson(1, eventId), Guest("new@example.com", true), description = "Private exercise")

        assertNull(result)
        assertEquals("Exercise 1", description(eventId))
    }

    @Test
    fun `refreshing a completed event retries a pending withdrawal before sharing homework`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.failGuestDelete = true
        invite(lesson(1, eventId), Guest("new@example.com", true))
        sync = calendar()

        sync.refresh(lesson(1, eventId), "Аня — Алгебра", description = "Private exercise")

        assertEquals("Exercise 1", description(eventId))
        provider.failGuestDelete = false
        sync.refresh(lesson(1, eventId), "Аня — Алгебра", description = "Private exercise")
        assertEquals(emptySet<String>(), provider.guestEmails(eventId.toLong()))
        assertEquals("Private exercise", description(eventId))
    }

    @Test
    fun `an event update failure does not create a duplicate event`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.failEventUpdate = true

        assertNull(invite(lesson(1, eventId), Guest("old@example.com", true)))

        assertEquals(setOf(eventId.toLong()), provider.events.keys)
    }

    @Test
    fun `the first correction after upgrading retires the previous untracked address`() = runTest {
        val eventId = legacyEvent("old@example.com")
        provider.addGuest(eventId.toLong(), "manual@example.com")

        invite(lesson(1, eventId), Guest("new@example.com", true, previousEmail = "old@example.com"))

        assertEquals(setOf("new@example.com", "manual@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `the first clearing after upgrading retires the previous untracked address`() = runTest {
        val eventId = legacyEvent("old@example.com")

        invite(lesson(1, eventId), Guest("", false, previousEmail = "old@example.com"))

        assertEquals(emptySet<String>(), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `turning invitations off adopts only the legacy student's matching address`() = runTest {
        val eventId = legacyEvent("old@example.com")
        provider.addGuest(eventId.toLong(), "manual@example.com")

        invite(lesson(1, eventId), Guest("old@example.com", false))

        assertEquals(setOf("manual@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `a pre-edit address is kept for retry even while calendar permissions are revoked`() = runTest {
        val eventId = legacyEvent("old@example.com")
        sync = calendar(FakeCalendarPermission(granted = false))

        assertNull(invite(lesson(1, eventId), Guest("new@example.com", true, "old@example.com")))
        sync = calendar()
        invite(lesson(1, eventId), Guest("latest@example.com", true))

        assertEquals(setOf("latest@example.com"), provider.guestEmails(eventId.toLong()))
    }

    @Test
    fun `a failed insertion keeps ownership so a later retry can add the invitation once`() = runTest {
        val eventId = invite(lesson(1), guest = null)!!
        provider.failGuestInsert = true

        assertNull(invite(lesson(1, eventId), Guest("new@example.com", true)))
        provider.failGuestInsert = false
        sync = calendar()
        invite(lesson(1, eventId), Guest("new@example.com", true))
        invite(lesson(1, eventId), Guest("new@example.com", true))

        assertEquals(listOf("new@example.com"), provider.guestAddresses(eventId.toLong()))
    }

    @Test
    fun `a new event keeps its binding when its invitation cannot be inserted`() = runTest {
        provider.failGuestInsert = true

        val eventId = invite(lesson(1), Guest("new@example.com", true))!!

        provider.failGuestInsert = false
        sync = calendar()
        assertEquals(eventId, invite(lesson(1, eventId), Guest("new@example.com", true)))
        assertEquals(setOf(eventId.toLong()), provider.events.keys)
        assertEquals(listOf("new@example.com"), provider.guestAddresses(eventId.toLong()))
    }

    @Test
    fun `an unsuccessful deletion result withholds new event content`() = runTest {
        val eventId = invite(lesson(1), Guest("old@example.com", true))!!
        provider.ignoreGuestDelete = true

        assertNull(invite(lesson(1, eventId), Guest("new@example.com", true), description = "Private exercise"))

        assertEquals("Exercise 1", description(eventId))
        assertEquals(setOf("old@example.com"), provider.guestEmails(eventId.toLong()))
    }
}

/** The calendar provider's tables, as far as the adapter writes them. */
class FakeCalendarProvider : ContentProvider() {
    val events = mutableMapOf<Long, ContentValues>()
    private val reminders = mutableListOf<ContentValues>()
    private val guests = mutableListOf<ContentValues>()
    var failGuestDelete = false
    var ignoreGuestDelete = false
    var failGuestQuery = false
    var failGuestInsert = false
    var failEventUpdate = false
    var calendarQueries = 0
        private set
    private var nextId = 1L

    fun reminderLeads(): List<Pair<Long, Int>> = reminders.map {
        it.getAsLong(CalendarContract.Reminders.EVENT_ID) to it.getAsInteger(CalendarContract.Reminders.MINUTES)
    }

    fun addGuest(eventId: Long, email: String) {
        guests += ContentValues().apply {
            put(CalendarContract.Attendees.EVENT_ID, eventId)
            put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
        }
    }

    fun guestAddresses(eventId: Long): List<String> = guests.filter {
        it.getAsLong(CalendarContract.Attendees.EVENT_ID) == eventId
    }.map { it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) }

    fun guestEmails(eventId: Long): Set<String> = guestAddresses(eventId).toSet()

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: emptyArray()).apply {
        when (uri.pathSegments.first()) {
            "calendars" -> {
                calendarQueries++
                addRow(arrayOf<Any>(7L, 1, 1, CalendarContract.Calendars.CAL_ACCESS_OWNER))
            }
            "attendees" -> {
                check(!failGuestQuery) { "Attendee query failed" }
                val eventId = selectionArgs!!.first().toLong()
                guests.filter { it.getAsLong(CalendarContract.Attendees.EVENT_ID) == eventId }.forEach { guest ->
                    addRow(projection!!.map { guest[it] }.toTypedArray())
                }
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = when (uri.pathSegments.first()) {
        "events" -> nextId++.also { events[it] = ContentValues(values) }.let { ContentUris.withAppendedId(uri, it) }
        "reminders" -> {
            reminders += ContentValues(values)
            ContentUris.withAppendedId(uri, reminders.size.toLong())
        }
        "attendees" -> {
            if (failGuestInsert) null else {
                guests += ContentValues(values)
                ContentUris.withAppendedId(uri, guests.size.toLong())
            }
        }
        else -> null
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = ContentUris.parseId(uri)
        check(!failEventUpdate) { "Event update failed" }
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
            "attendees" -> {
                check(!failGuestDelete) { "Attendee deletion failed" }
                val eventId = selectionArgs!!.first().toLong()
                val email = selectionArgs[1]
                val before = guests.size
                if (!ignoreGuestDelete) guests.removeAll {
                    it.getAsLong(CalendarContract.Attendees.EVENT_ID) == eventId &&
                        it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) == email
                }
                before - guests.size
            }
            else -> 0
        }

    override fun getType(uri: Uri): String? = null
}
