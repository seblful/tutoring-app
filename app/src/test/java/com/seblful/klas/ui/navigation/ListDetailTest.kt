package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListDetailTest {

    private val navigator = KlasNavigator()

    private fun stack(): List<String> = navigator.backStack.map { it.routeName() }

    @Test
    fun `a card opened from the People list replaces the one beside it`() {
        navigator.navigateToTab(TopDestination.PEOPLE)
        navigator.replaceAbove(Routes.People, Routes.StudentDetail(1))

        navigator.replaceAbove(Routes.People, Routes.GroupDetail(2))

        assertEquals(listOf("Schedule", "People", "GroupDetail"), stack())
    }

    @Test
    fun `a member opened from a group card stacks on it, so Back returns to the group`() {
        navigator.navigateToTab(TopDestination.PEOPLE)
        navigator.replaceAbove(Routes.People, Routes.GroupDetail(2))

        navigator.navigate(Routes.StudentDetail(1))

        assertEquals(listOf("Schedule", "People", "GroupDetail", "StudentDetail"), stack())
        assertEquals(Routes.People, listBesideDetail(navigator.backStack))
    }

    @Test
    fun `a card is beside the list only with the list under it`() {
        navigator.navigate(Routes.StudentDetail(1))
        assertNull("opened from the schedule", listBesideDetail(navigator.backStack))

        navigator.navigateToTab(TopDestination.PEOPLE)
        assertNull("the list alone", listBesideDetail(navigator.backStack))

        navigator.replaceAbove(Routes.People, Routes.StudentDetail(1))
        assertEquals(Routes.People, listBesideDetail(navigator.backStack))

        navigator.navigate(Routes.classEdit(0, studentId = 1))
        assertNull("a form over the card owns the window", listBesideDetail(navigator.backStack))
    }

    @Test
    fun `a settings page opened from the index replaces the one beside it`() {
        navigator.navigateToTab(TopDestination.MORE)
        navigator.navigate(Routes.Settings)
        navigator.replaceAbove(Routes.Settings, Routes.LessonSettings)

        navigator.replaceAbove(Routes.Settings, Routes.Diagnostics)

        assertEquals(listOf("Schedule", "More", "Settings", "Diagnostics"), stack())
        assertEquals(Routes.Settings, listBesideDetail(navigator.backStack))
    }

    @Test
    fun `a detail is beside only its own list`() {
        navigator.navigateToTab(TopDestination.PEOPLE)
        navigator.navigate(Routes.LessonSettings)

        assertNull(listBesideDetail(navigator.backStack))
    }

    @Test
    fun `the index's back arrow leaves Settings with the page beside it`() {
        navigator.navigateToTab(TopDestination.MORE)
        navigator.navigate(Routes.Settings)
        navigator.replaceAbove(Routes.Settings, Routes.CalendarSettings)

        navigator.closeFrom(Routes.Settings)

        assertEquals(listOf("Schedule", "More"), stack())
    }

    @Test
    fun `closing a list that is not open keeps the stack`() {
        navigator.closeFrom(Routes.Settings)
        navigator.closeFrom(Routes.Schedule)

        assertEquals(listOf("Schedule"), stack())
    }
}
