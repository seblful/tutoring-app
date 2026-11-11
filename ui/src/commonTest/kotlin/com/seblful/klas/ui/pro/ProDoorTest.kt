package com.seblful.klas.ui.pro

import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.ProFeature
import com.seblful.klas.domain.pro.ProGate
import com.seblful.klas.domain.pro.ProSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProDoorTest {

    private val opened = mutableListOf<ProFeature?>()

    private fun door(gate: ProGate?) = ProDoor(gate = { gate }, open = { opened += it })

    @Test
    fun `a free tutor reaching for a Pro feature is shown Klas Pro instead`() {
        var ran = false

        door(ProGate(Access.Free, activeStudents = 0)).guard(ProFeature.CALENDAR) { ran = true }

        assertEquals(false, ran)
        assertEquals(listOf<ProFeature?>(ProFeature.CALENDAR), opened)
    }

    @Test
    fun `a free tutor with room adds a student`() {
        var ran = false

        door(ProGate(Access.Free, activeStudents = 4)).guard(ProFeature.STUDENTS) { ran = true }

        assertTrue(ran)
        assertEquals(emptyList<ProFeature?>(), opened)
    }

    @Test
    fun `a Pro tutor goes straight through`() {
        var ran = false

        door(ProGate(Access.Pro(ProSource.MONTHLY), activeStudents = 9)).guard(ProFeature.GROUPS) { ran = true }

        assertTrue(ran)
    }

    @Test
    fun `nothing is decided before the gate is read and nothing is locked meanwhile`() {
        val unread = door(null)

        assertNull(unread.decided(ProFeature.REPORTS))
        assertTrue(unread.allows(ProFeature.REPORTS))
    }
}
