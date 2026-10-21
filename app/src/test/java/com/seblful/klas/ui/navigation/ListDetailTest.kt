package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue(cardBesideList(navigator.backStack))
    }

    @Test
    fun `a card is beside the list only with the list under it`() {
        navigator.navigate(Routes.StudentDetail(1))
        assertFalse("opened from the schedule", cardBesideList(navigator.backStack))

        navigator.navigateToTab(TopDestination.PEOPLE)
        assertFalse("the list alone", cardBesideList(navigator.backStack))

        navigator.replaceAbove(Routes.People, Routes.StudentDetail(1))
        assertTrue(cardBesideList(navigator.backStack))

        navigator.navigate(Routes.classEdit(0, studentId = 1))
        assertFalse("a form over the card owns the window", cardBesideList(navigator.backStack))
    }
}
