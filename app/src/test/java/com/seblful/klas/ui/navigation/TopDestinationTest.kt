package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopDestinationTest {

    @Test
    fun `the tabs read who, then what they were set, then when, then the money`() {
        assertEquals(
            listOf(Routes.People, Routes.Homework, Routes.Schedule, Routes.Reports, Routes.More),
            TopDestination.entries.map { it.route },
        )
    }

    @Test
    fun `the tab the app opens on sits in the middle of the bar`() {
        assertEquals(TopDestination.entries.size / 2, TopDestination.SCHEDULE.ordinal)
    }

    @Test
    fun `the screens reached from a tab are not tabs`() {
        val tabRoutes = TopDestination.entries.map { it.route }
        listOf(Routes.Subjects, Routes.Settings, Routes.Backup, Routes.Diagnostics, Routes.About)
            .forEach { assertFalse("$it", it in tabRoutes) }
    }

    @Test
    fun `groups is not a top-level destination`() {
        assertTrue(TopDestination.entries.none { it.route::class.simpleName.orEmpty().contains("Group") })
    }
}
