package com.seblful.klas.domain.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneSetupTest {

    @Test
    fun `only what the old phone had is asked for - in the card's order`() {
        val setup = PhoneSetup.of(
            wanted = setOf(PhoneStep.WEEKLY_COPY, PhoneStep.REMINDERS),
            reminded = false,
            mirrored = false,
            copying = false,
        )
        assertEquals(listOf(PhoneStep.REMINDERS, PhoneStep.WEEKLY_COPY), setup.steps)
    }

    @Test
    fun `a step done but not wanted does not count towards the card`() {
        val setup = PhoneSetup.of(setOf(PhoneStep.REMINDERS), reminded = false, mirrored = true, copying = true)
        assertEquals(emptySet(), setup.done)
        assertFalse(setup.complete)
    }

    @Test
    fun `every wanted step on this phone completes it`() {
        val setup = PhoneSetup.of(PhoneSetup.UNKNOWN, reminded = true, mirrored = false, copying = true)
        assertTrue(setup.complete)
    }

    @Test
    fun `a file that does not say asks for reminders and the weekly copy - not the calendar`() {
        assertEquals(setOf(PhoneStep.REMINDERS, PhoneStep.WEEKLY_COPY), PhoneSetup.UNKNOWN)
    }
}
