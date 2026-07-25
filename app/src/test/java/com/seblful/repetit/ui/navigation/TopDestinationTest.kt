package com.seblful.repetit.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar is derived from one list, so the tab order and the "is this route a tab" test can't
 * drift apart — the bug that would otherwise show up as a bar rendered with nothing selected.
 */
class TopDestinationTest {

    @Test
    fun `the tabs read who, then when, then the money`() {
        assertEquals(
            listOf(Routes.STUDENTS, Routes.GROUPS, Routes.SCHEDULE, Routes.REPORTS, Routes.MORE),
            TopDestination.visible(showGroupsTab = true).map { it.route },
        )
    }

    @Test
    fun `switching the groups tab off drops it and leaves the rest in order`() {
        assertEquals(
            listOf(Routes.STUDENTS, Routes.SCHEDULE, Routes.REPORTS, Routes.MORE),
            TopDestination.visible(showGroupsTab = false).map { it.route },
        )
    }

    @Test
    fun `a hidden groups tab is not a tab route, so the bar stays off when it opens from More`() {
        assertTrue(Routes.GROUPS in TopDestination.routes(showGroupsTab = true))
        assertFalse(Routes.GROUPS in TopDestination.routes(showGroupsTab = false))
        // Every other tab is unaffected either way.
        assertTrue(TopDestination.routes(showGroupsTab = false).containsAll(
            listOf(Routes.STUDENTS, Routes.SCHEDULE, Routes.REPORTS, Routes.MORE),
        ))
    }
}
