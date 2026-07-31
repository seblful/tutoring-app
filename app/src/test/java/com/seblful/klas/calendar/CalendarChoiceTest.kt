package com.seblful.klas.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which calendar a lesson is mirrored into, and what an event's guest list owes the student.
 *
 * Both were private to `DeviceCalendarSync` — 200 lines of `ContentResolver` with no test in either
 * source set — and the first of them has already shipped wrong: demanding OWNER matched nothing on
 * devices that report a synced Google calendar as EDITOR or CONTRIBUTOR, so every event those phones
 * thought they mirrored silently no-opped.
 */
class CalendarChoiceTest {

    private fun row(
        id: Long,
        primary: Boolean = false,
        visible: Boolean = true,
        access: Int = CalendarChoice.MIN_ACCESS_LEVEL,
    ) = CalendarRow(id = id, isPrimary = primary, visible = visible, accessLevel = access)

    @Test
    fun `no calendars means nowhere to write`() {
        assertNull(CalendarChoice.preferred(emptyList()))
    }

    /** The whole of the shipped bug: a contributor-level calendar is writable and must be found. */
    @Test
    fun `a contributor calendar is writable`() {
        assertEquals(7L, CalendarChoice.preferred(listOf(row(7, access = CalendarChoice.MIN_ACCESS_LEVEL))))
    }

    @Test
    fun `a read-only calendar is not offered`() {
        val readOnly = row(7, access = CalendarChoice.MIN_ACCESS_LEVEL - 1)

        assertNull(CalendarChoice.preferred(listOf(readOnly)))
    }

    @Test
    fun `the account's primary wins, whatever else is on offer`() {
        val rows = listOf(
            row(1, access = 700),
            row(2, primary = true, access = CalendarChoice.MIN_ACCESS_LEVEL),
            row(3, access = 800),
        )

        assertEquals(2L, CalendarChoice.preferred(rows))
    }

    /** A hidden calendar is a real place to write, but the tutor would never see the event in it. */
    @Test
    fun `a visible calendar beats a hidden one`() {
        val rows = listOf(row(1, visible = false, access = 800), row(2, visible = true, access = 600))

        assertEquals(2L, CalendarChoice.preferred(rows))
    }

    @Test
    fun `among equals the more privileged one wins`() {
        val rows = listOf(row(1, access = 600), row(2, access = 800))

        assertEquals(2L, CalendarChoice.preferred(rows))
    }

    /** A primary the app may not write to is not a candidate at all — the rule filters before it ranks. */
    @Test
    fun `an unwritable primary does not shadow a writable one`() {
        val rows = listOf(
            row(1, primary = true, access = CalendarChoice.MIN_ACCESS_LEVEL - 1),
            row(2, access = CalendarChoice.MIN_ACCESS_LEVEL),
        )

        assertEquals(2L, CalendarChoice.preferred(rows))
    }

    @Test
    fun `ties fall to whichever the platform listed first`() {
        val rows = listOf(row(1), row(2))

        assertEquals(1L, CalendarChoice.preferred(rows))
    }
}

/**
 * Inviting the student, and taking the invitation back — the two properties that matter are that it
 * touches exactly one address and that it is idempotent in both directions.
 */
class GuestActionTest {

    private val anya = "anya@example.com"
    private val tutorsOwnGuest = "kolleg@example.com"

    @Test
    fun `a student with no guest record is left alone`() {
        assertEquals(GuestAction.None, guestAction(listOf(anya), guest = null))
    }

    /** «Don't invite» is not «no address» — but a blank address really is nothing to act on. */
    @Test
    fun `a blank address is nothing to act on`() {
        assertEquals(GuestAction.None, guestAction(emptyList(), Guest(email = "", invited = true)))
        assertEquals(GuestAction.None, guestAction(emptyList(), Guest(email = "", invited = false)))
    }

    @Test
    fun `an invited student not yet on the event is added`() {
        assertEquals(
            GuestAction.Add(anya),
            guestAction(listOf(tutorsOwnGuest), Guest(anya, invited = true)),
        )
    }

    @Test
    fun `an invited student already on the event is left alone`() {
        assertEquals(
            GuestAction.None,
            guestAction(listOf(tutorsOwnGuest, anya), Guest(anya, invited = true)),
        )
    }

    /** Turning invites off withdraws what was already sent — see [Guest]. */
    @Test
    fun `an uninvited student already on the event is removed`() {
        assertEquals(
            GuestAction.Remove(anya),
            guestAction(listOf(anya, tutorsOwnGuest), Guest(anya, invited = false)),
        )
    }

    @Test
    fun `an uninvited student who was never added is left alone`() {
        assertEquals(
            GuestAction.None,
            guestAction(listOf(tutorsOwnGuest), Guest(anya, invited = false)),
        )
    }

    /**
     * The property that keeps a re-sync from clearing the tutor's own calendar: no answer here ever
     * names an address other than the student's.
     */
    @Test
    fun `guests the tutor added are never named by any answer`() {
        val others = listOf(tutorsOwnGuest, "mama@example.com")

        val added = guestAction(others, Guest(anya, invited = true))
        val removed = guestAction(others + anya, Guest(anya, invited = false))

        assertEquals(GuestAction.Add(anya), added)
        assertEquals(GuestAction.Remove(anya), removed)
    }
}
