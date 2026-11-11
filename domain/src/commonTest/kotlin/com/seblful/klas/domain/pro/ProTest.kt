package com.seblful.klas.domain.pro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ProTest {

    @Test
    fun `an early user is Pro whatever the store says`() {
        assertEquals(Access.Pro(ProSource.EARLY_USER), Access.of(earlyUser = true, owned = null, storeSells = true))
        assertEquals(
            Access.Pro(ProSource.EARLY_USER),
            Access.of(earlyUser = true, owned = ProPlan.MONTHLY, storeSells = true),
        )
    }

    @Test
    fun `a purchase is Pro by its plan`() {
        val lifetime = Access.of(earlyUser = false, owned = ProPlan.LIFETIME, storeSells = true)
        val monthly = Access.of(earlyUser = false, owned = ProPlan.MONTHLY, storeSells = true)
        assertEquals(Access.Pro(ProSource.LIFETIME), lifetime)
        assertEquals(Access.Pro(ProSource.MONTHLY), monthly)
    }

    @Test
    fun `nothing owned is free where a store sells Pro`() {
        assertEquals(Access.Free, Access.of(earlyUser = false, owned = null, storeSells = true))
    }

    @Test
    fun `nothing is locked where no store sells Pro`() {
        assertEquals(Access.Pro(ProSource.NO_STORE), Access.of(earlyUser = false, owned = null, storeSells = false))
    }

    @Test
    fun `a free tutor has room for five active students`() {
        assertTrue(FreeTier.roomForStudent(Access.Free, active = 4))
        assertFalse(FreeTier.roomForStudent(Access.Free, active = 5))
        assertFalse(FreeTier.roomForStudent(Access.Free, active = 8))
    }

    @Test
    fun `a free gate counts students and refuses the rest`() {
        val gate = ProGate(Access.Free, activeStudents = 4)
        assertTrue(gate.allows(ProFeature.STUDENTS))
        assertFalse(gate.copy(activeStudents = 5).allows(ProFeature.STUDENTS))
        ProFeature.entries.filter { it != ProFeature.STUDENTS }.forEach { assertFalse(gate.allows(it), it.name) }
    }

    @Test
    fun `a Pro gate allows everything`() {
        val gate = ProGate(Access.Pro(ProSource.LIFETIME), activeStudents = 40)
        ProFeature.entries.forEach { assertTrue(gate.allows(it), it.name) }
    }

    @Test
    fun `the Pro icon waits out a new tutor's first week or their first steps`() {
        val setup = Instant.parse("2026-11-01T10:00:00Z")
        assertFalse(ProOffer.shown(setup, setup + 6.days, firstStepsHidden = false))
        assertTrue(ProOffer.shown(setup, setup + 7.days, firstStepsHidden = false))
        assertTrue(ProOffer.shown(setup, setup + 1.days, firstStepsHidden = true))
        assertTrue(ProOffer.shown(setupAt = null, now = setup, firstStepsHidden = false))
    }

    @Test
    fun `Pro has room for any number of students`() {
        assertTrue(FreeTier.roomForStudent(Access.Pro(ProSource.MONTHLY), active = 500))
    }
}
