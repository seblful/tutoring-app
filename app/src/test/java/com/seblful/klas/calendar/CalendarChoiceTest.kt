package com.seblful.klas.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

class GuestPlanTest {

    private val anya = "anya@example.com"
    private val tutorsOwnGuest = "kolleg@example.com"

    @Test
    fun `a student with no guest record is left alone`() {
        assertEquals(GuestPlan(emptySet(), emptySet(), null), guestPlan(setOf(anya), null, guest = null))
    }

    /** «Don't invite» is not «no address» — but a blank address really is nothing to act on. */
    @Test
    fun `a blank address is nothing to act on`() {
        val nothing = GuestPlan(emptySet(), emptySet(), null)
        assertEquals(nothing, guestPlan(emptySet(), null, Guest(email = "", invited = true)))
        assertEquals(nothing, guestPlan(emptySet(), null, Guest(email = "", invited = false)))
    }

    @Test
    fun `an invited student not yet on the event is added`() {
        assertEquals(
            GuestPlan(emptySet(), emptySet(), anya),
            guestPlan(setOf(tutorsOwnGuest), null, Guest(anya, invited = true)),
        )
    }

    @Test
    fun `an invited student already on the event is left alone`() {
        assertEquals(
            GuestPlan(setOf(anya), emptySet(), null),
            guestPlan(setOf(tutorsOwnGuest, anya), null, Guest(anya, invited = true)),
        )
    }

    @Test
    fun `an uninvited student already on the event is removed`() {
        assertEquals(
            GuestPlan(setOf(anya), setOf(anya), null),
            guestPlan(setOf(anya, tutorsOwnGuest), null, Guest(anya, invited = false)),
        )
    }

    @Test
    fun `an uninvited student who was never added is left alone`() {
        assertEquals(
            GuestPlan(emptySet(), emptySet(), null),
            guestPlan(setOf(tutorsOwnGuest), null, Guest(anya, invited = false)),
        )
    }

    @Test
    fun `guests the tutor added are never named by any answer`() {
        val others = setOf(tutorsOwnGuest, "mama@example.com")

        val added = guestPlan(others, emptySet(), Guest(anya, invited = true))
        val removed = guestPlan(others + anya, setOf(anya), Guest(anya, invited = false))

        assertEquals(anya, added.add)
        assertEquals(setOf(anya), removed.remove)
    }

    @Test
    fun `clearing the guest retires the managed address`() {
        assertEquals(
            GuestPlan(setOf(anya), setOf(anya), null),
            guestPlan(setOf(anya, tutorsOwnGuest), setOf(anya), guest = null),
        )
    }

    @Test
    fun `a matching manual guest on a tracked event is not adopted`() {
        assertEquals(
            GuestPlan(emptySet(), emptySet(), null),
            guestPlan(setOf(anya), emptySet(), Guest(anya, invited = false)),
        )
    }

    @Test
    fun `changing the address retires every pending managed recipient`() {
        val old = setOf("old@example.com", "older@example.com")
        assertEquals(
            GuestPlan(old, old, anya),
            guestPlan(old + tutorsOwnGuest, old, Guest(anya, invited = true)),
        )
    }
}
