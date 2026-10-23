package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateToTabTest {

    private val navigator = KlasNavigator()

    private fun stack(): List<String> = navigator.backStack.map { it.routeName() }

    private fun showing(): TopDestination? = TopDestination.of(navigator.top.route)

    private fun tap(tab: TopDestination) = navigator.navigateToTab(tab)

    @Test
    fun `a screen closes only itself`() {
        tap(TopDestination.PEOPLE)
        navigator.navigate(Routes.StudentDetail(1))
        val card = navigator.top

        navigator.close(card)
        navigator.close(card)

        assertEquals(listOf("Today", "People"), stack())
    }

    @Test
    fun `tray arrivals preserve an unfinished form from the same tab and another tab`() {
        navigator.navigate(Routes.classEdit(0))
        val draft = navigator.top

        for (tab in listOf(TopDestination.SCHEDULE, TopDestination.REPORTS)) {
            navigator.openTabFromTray(tab)
            assertEquals(tab, showing())
            navigator.goBack()
            assertSame(draft, navigator.top)
        }
    }

    @Test
    fun `settings opened from a tab's gear belong to that tab and return through the index`() {
        tap(TopDestination.PEOPLE)
        navigator.openOnTop(Routes.Settings)
        navigator.openOnTop(Routes.Settings)
        for (category in listOf(
            Routes.LessonSettings, Routes.NotificationSettings, Routes.CalendarSettings, Routes.AppearanceSettings,
        )) {
            navigator.navigate(category)
            assertEquals(TopDestination.PEOPLE, owningTab(navigator.backStack))
            navigator.goBack()
            assertEquals(listOf("Today", "People", "Settings"), stack())
        }
        navigator.navigate(Routes.CalendarSettings)
        tap(TopDestination.SCHEDULE)
        tap(TopDestination.PEOPLE)
        assertEquals(listOf("Today", "People", "Settings", "CalendarSettings"), stack())
        tap(TopDestination.PEOPLE)
        assertEquals(listOf("Today", "People"), stack())
    }

    @Test
    fun `a tap on the tab that is showing goes nowhere`() {
        tap(TopDestination.HOMEWORK)
        val shown = navigator.backStack.toList()

        tap(TopDestination.HOMEWORK)
        tap(TopDestination.HOMEWORK)

        assertEquals(shown, navigator.backStack)
        assertEquals(TopDestination.HOMEWORK, showing())
    }

    @Test
    fun `only the tab whose own screen is on top counts as tapped again`() {
        tap(TopDestination.PEOPLE)
        assertTrue(navigator.isOnTab(TopDestination.PEOPLE))
        assertFalse(navigator.isOnTab(TopDestination.TODAY))

        // A card over the list: the first tap closes it, so this one is not a reselect.
        navigator.navigate(Routes.StudentDetail(1))
        assertFalse(navigator.isOnTab(TopDestination.PEOPLE))
    }

    @Test
    fun `a tap on the start tab while it shows goes nowhere either`() {
        val shown = navigator.backStack.toList()

        tap(TopDestination.START)

        assertEquals(shown, navigator.backStack)
    }

    @Test
    fun `a tap on another tab switches to it`() {
        tap(TopDestination.HOMEWORK)
        assertEquals(TopDestination.HOMEWORK, showing())
        tap(TopDestination.PEOPLE)
        assertEquals(TopDestination.PEOPLE, showing())
        tap(TopDestination.HOMEWORK)
        assertEquals(TopDestination.HOMEWORK, showing())
    }

    @Test
    fun `a tap on the start tab closes a form opened from it`() {
        navigator.navigate(Routes.classEdit(5))

        tap(TopDestination.START)

        assertEquals(listOf("Today"), stack())
    }

    @Test
    fun `a tap on the reports closes a card opened from them`() {
        tap(TopDestination.REPORTS)
        navigator.navigate(Routes.StudentDetail(1))

        tap(TopDestination.REPORTS)

        assertEquals(listOf("Today", "Reports"), stack())
        assertEquals(TopDestination.REPORTS, showing())
    }

    @Test
    fun `a screen pushed over another tab waits with it and comes back with it`() {
        tap(TopDestination.PEOPLE)
        navigator.navigate(Routes.StudentDetail(1))
        val card = navigator.top

        tap(TopDestination.START)
        assertEquals(listOf("Today"), stack())
        assertEquals("a waiting card stays live, so it keeps its state", true, card in navigator.live)

        tap(TopDestination.PEOPLE)
        assertEquals(listOf("Today", "People", "StudentDetail"), stack())
        assertSame(card, navigator.top)
    }

    @Test
    fun `a form opened over the start tab waits with it while another tab shows`() {
        navigator.navigate(Routes.classEdit(5))
        navigator.openTabFromTray(TopDestination.SCHEDULE)

        tap(TopDestination.REPORTS)
        tap(TopDestination.SCHEDULE)

        assertEquals(listOf("Today", "ClassEdit", "Schedule"), stack())
    }

    @Test
    fun `back from the start tab is left to the system`() {
        navigator.goBack()

        assertEquals(listOf("Today"), stack())
    }

    @Test
    fun `the same card opened twice is two screens`() {
        navigator.navigate(Routes.StudentDetail(1))
        navigator.navigate(Routes.GroupDetail(2))
        navigator.navigate(Routes.StudentDetail(1))

        assertEquals(navigator.backStack.size, navigator.backStack.map { it.id }.toSet().size)
    }
}
