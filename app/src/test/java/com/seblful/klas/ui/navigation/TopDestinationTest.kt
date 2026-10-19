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
    fun `the tabs read who, then what they were set, then when, then the money`() {
        assertEquals(
            listOf(Routes.People, Routes.Homework, Routes.Schedule, Routes.Reports, Routes.More),
            TopDestination.entries.map { it.route },
        )
    }

    /** The app opens on the schedule, and with five tabs the middle one is the easiest to reach. */
    @Test
    fun `the tab the app opens on sits in the middle of the bar`() {
        assertEquals(TopDestination.entries.size / 2, TopDestination.SCHEDULE.ordinal)
    }

    @Test
    fun `the screens reached from a tab are not tabs`() {
        // The bar hides for them, which is what gives them a back arrow instead of a selected tab.
        val tabRoutes = TopDestination.entries.map { it.route }
        listOf(Routes.Subjects, Routes.Settings, Routes.Backup, Routes.Diagnostics, Routes.About)
            .forEach { assertFalse("$it", it in tabRoutes) }
    }

    /**
     * Groups used to be a tab that could be switched off, which meant the bar, the route set and the
     * «Ещё» menu all had to agree about a boolean. They are a view inside «Люди» now, so there is no
     * groups *route* to be a tab at all — this is the tripwire for someone re-adding one without
     * re-adding the visibility machinery that made it safe.
     */
    @Test
    fun `groups is not a top-level destination`() {
        assertTrue(TopDestination.entries.none { it.route::class.simpleName.orEmpty().contains("Group") })
    }
}
