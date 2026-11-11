package com.seblful.klas.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrivalTest {

    @Test
    fun `an arrival is let through once`() {
        val gate = ArrivalGate()
        val tap = Arrival("today")

        assertEquals("today", gate.admit(tap))
        assertNull(gate.admit(tap), "handed over again on the tab's return")
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
    fun `an arrival stays owed until it is honoured — however often it is offered`() {
        val gate = ArrivalGate()
        val tap = Arrival(Unit)

        assertTrue(gate.owes(tap))
        assertTrue(gate.owes(tap), "asking must not spend it — the tab may leave before acting")
        gate.honour(tap)
        assertFalse(gate.owes(tap), "acted on once is acted on")
        assertTrue(gate.owes(Arrival(Unit)), "a second tap is a new ask")
    }

    @Test
    fun `no arrival is never owed — and honouring none changes nothing`() {
        val gate = ArrivalGate()
        val tap = Arrival(Unit)

        assertFalse(gate.owes<Unit>(null))
        gate.honour(null)
        assertTrue(gate.owes(tap))
    }
}
