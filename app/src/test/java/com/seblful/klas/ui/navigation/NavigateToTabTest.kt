package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

        assertEquals(listOf("Schedule", "People"), stack())
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
    fun `settings categories return through the index and retain More tab ownership`() {
        tap(TopDestination.MORE)
        navigator.navigate(Routes.Settings)
        for (category in listOf(
            Routes.LessonSettings, Routes.NotificationSettings, Routes.CalendarSettings, Routes.AppearanceSettings,
        )) {
            navigator.navigate(category)
            assertEquals(TopDestination.MORE, owningTab(navigator.backStack))
            navigator.goBack()
            assertEquals(listOf("Schedule", "More", "Settings"), stack())
        }
        navigator.navigate(Routes.CalendarSettings)
        tap(TopDestination.SCHEDULE)
        tap(TopDestination.MORE)
        assertEquals(listOf("Schedule", "More", "Settings", "CalendarSettings"), stack())
        tap(TopDestination.MORE)
        assertEquals(listOf("Schedule", "More"), stack())
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
    fun `a tap on the start tab while it shows goes nowhere either`() {
        val shown = navigator.backStack.toList()

        tap(TopDestination.SCHEDULE)

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
    fun `a tap on the schedule closes a form opened from it`() {
        navigator.navigate(Routes.classEdit(5))

        tap(TopDestination.SCHEDULE)

        assertEquals(listOf("Schedule"), stack())
    }

    @Test
    fun `a tap on the reports closes a card opened from them`() {
        tap(TopDestination.REPORTS)
        navigator.navigate(Routes.StudentDetail(1))

        tap(TopDestination.REPORTS)

        assertEquals(listOf("Schedule", "Reports"), stack())
        assertEquals(TopDestination.REPORTS, showing())
    }

    @Test
    fun `a screen pushed over another tab waits with it and comes back with it`() {
        tap(TopDestination.PEOPLE)
        navigator.navigate(Routes.StudentDetail(1))
        val card = navigator.top

        tap(TopDestination.SCHEDULE)
        assertEquals(listOf("Schedule"), stack())
        assertEquals("a waiting card stays live, so it keeps its state", true, card in navigator.live)

        tap(TopDestination.PEOPLE)
        assertEquals(listOf("Schedule", "People", "StudentDetail"), stack())
        assertSame(card, navigator.top)
    }

    @Test
    fun `a form opened over the schedule waits with it while another tab shows`() {
        navigator.navigate(Routes.classEdit(5))
        navigator.openTabFromTray(TopDestination.SCHEDULE)

        tap(TopDestination.REPORTS)
        tap(TopDestination.SCHEDULE)

        assertEquals(listOf("Schedule", "ClassEdit", "Schedule"), stack())
    }

    @Test
    fun `back from the start tab is left to the system`() {
        navigator.goBack()

        assertEquals(listOf("Schedule"), stack())
    }

    @Test
    fun `the same card opened twice is two screens`() {
        navigator.navigate(Routes.StudentDetail(1))
        navigator.navigate(Routes.GroupDetail(2))
        navigator.navigate(Routes.StudentDetail(1))

        assertEquals(navigator.backStack.size, navigator.backStack.map { it.id }.toSet().size)
    }
}
