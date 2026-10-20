package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `an arrival stays owed until it is honoured, however often it is offered`() {
        val gate = ArrivalGate()
        val tap = Arrival(Unit)

        assertTrue(gate.owes(tap))
        assertTrue("asking must not spend it — the tab may leave before acting", gate.owes(tap))
        gate.honour(tap)
        assertFalse("acted on once is acted on", gate.owes(tap))
        assertTrue("a second tap is a new ask", gate.owes(Arrival(Unit)))
    }

    @Test
    fun `no arrival is never owed, and honouring none changes nothing`() {
        val gate = ArrivalGate()
        val tap = Arrival(Unit)

        assertFalse(gate.owes<Unit>(null))
        gate.honour(null)
        assertTrue(gate.owes(tap))
    }
}
