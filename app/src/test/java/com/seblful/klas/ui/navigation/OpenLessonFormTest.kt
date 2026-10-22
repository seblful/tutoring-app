package com.seblful.klas.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenLessonFormTest {

    private val navigator = KlasNavigator()

    private fun open(classId: Long) = navigator.openLessonForm(classId)

    private fun stack(): List<String> = navigator.backStack.map { screen ->
        (screen.route as? Routes.ClassEdit)?.let { "form ${it.id}" } ?: screen.routeName()
    }

    @Test
    fun `a tap opens the lesson's form`() {
        open(5)

        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `a second tap on the same notification finds the form already open`() {
        open(5)
        open(5)

        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `another lesson's form opens over the one being edited, which waits under it`() {
        open(5)

        open(7)

        assertEquals(listOf("Schedule", "form 5", "form 7"), stack())
        navigator.goBack()
        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `a tap opens the form over a screen that is already pushed`() {
        navigator.navigateToTab(TopDestination.MORE)
        navigator.navigate(Routes.Settings)

        open(7)

        assertEquals(listOf("Schedule", "More", "Settings", "form 7"), stack())
    }

    @Test
    fun `a widget's new-lesson form opens once, however often its button is tapped`() {
        navigator.openOnTop(Routes.classEdit(0))
        navigator.openOnTop(Routes.classEdit(0))

        assertEquals(listOf("Schedule", "form 0"), stack())
    }

    @Test
    fun `a widget's student form opens over the booking being typed, which waits under it`() {
        navigator.openOnTop(Routes.classEdit(0))

        navigator.openOnTop(Routes.StudentAdd)

        assertEquals(listOf("Schedule", "form 0", "StudentAdd"), stack())
    }
}
