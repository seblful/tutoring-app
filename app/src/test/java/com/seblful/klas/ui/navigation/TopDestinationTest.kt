package com.seblful.klas.ui.navigation

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
            listOf(Routes.PEOPLE, Routes.SCHEDULE, Routes.REPORTS, Routes.MORE),
            TopDestination.entries.map { it.route },
        )
    }

    @Test
    fun `every tab's route is a tab route, and nothing else is`() {
        assertEquals(TopDestination.entries.map { it.route }.toSet(), TopDestination.routes)
        // The sub-screens reached from a tab are not tabs: the bar hides for them, which is what
        // gives them a back arrow instead of a selected tab.
        listOf(Routes.SUBJECTS, Routes.SETTINGS, Routes.BACKUP, Routes.DIAGNOSTICS, Routes.ABOUT)
            .forEach { assertFalse(it, it in TopDestination.routes) }
    }

    /**
     * Groups used to be a tab that could be switched off, which meant the bar, the route set and the
     * «Ещё» menu all had to agree about a boolean. They are a view inside «Люди» now, so there is no
     * groups *route* to be a tab at all — this is the tripwire for someone re-adding one without
     * re-adding the visibility machinery that made it safe.
     */
    @Test
    fun `groups is not a top-level destination`() {
        assertTrue(TopDestination.entries.none { it.route.contains("group") })
    }
}
