package com.seblful.klas.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopDestinationTest {

    @Test
    fun `the tabs read the day — then the week — then who — what they were set — and the money`() {
        assertEquals(
            listOf(Routes.Today, Routes.Schedule, Routes.People, Routes.Homework, Routes.Reports),
            TopDestination.entries.map { it.route },
        )
    }

    @Test
    fun `the app opens on the first tab`() {
        assertEquals(TopDestination.entries.first(), TopDestination.START)
    }

    @Test
    fun `the screens reached from a tab are not tabs`() {
        val tabRoutes = TopDestination.entries.map { it.route }
        listOf(Routes.Subjects, Routes.Settings, Routes.Backup, Routes.Diagnostics, Routes.About)
            .forEach { assertFalse(it in tabRoutes, "$it") }
    }

    @Test
    fun `groups is not a top-level destination`() {
        assertTrue(TopDestination.entries.none { it.route::class.simpleName.orEmpty().contains("Group") })
    }
}
