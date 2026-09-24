package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One tap in the tray is acted on once, however many times the tab it asked for comes back — and a second
 * tap on the same notification is a second tap, though it says exactly the same thing.
 */
class ArrivalTest {

    @Test
    fun `an arrival is let through once`() {
        val gate = ArrivalGate()
        val tap = Arrival("today")

        assertEquals("today", gate.admit(tap))
        assertNull("handed over again on the tab's return", gate.admit(tap))
    }

    @Test
    fun `a second tap saying the same thing is a new arrival`() {
        val gate = ArrivalGate()
        gate.admit(Arrival("today"))

        assertEquals("today", gate.admit(Arrival("today")))
    }

    @Test
    fun `no arrival is nothing to act on`() {
        assertNull(ArrivalGate().admit<String>(null))
    }
}
