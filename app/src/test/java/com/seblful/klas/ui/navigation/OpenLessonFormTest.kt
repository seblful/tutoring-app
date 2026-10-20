package com.seblful.klas.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenLessonFormTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun showGraph() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Routes.Schedule) {
                TopDestination.entries.forEach { tab -> screen(tab.route::class) {} }
                screen<Routes.Settings> {}
                screen<Routes.ClassEdit> {}
            }
        }
        compose.waitForIdle()
    }

    private fun onUiThread(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
    }

    private fun open(classId: Long) = onUiThread { navController.openLessonForm(classId) }

    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { entry ->
        if (entry.destination.hasRoute(Routes.ClassEdit::class)) {
            "form ${entry.toRoute<Routes.ClassEdit>().id}"
        } else {
            entry.destination.routeName()
        }
    }

    @Test
    fun `a tap opens the lesson's form`() {
        showGraph()

        open(5)

        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `a second tap on the same notification finds the form already open`() {
        showGraph()

        open(5)
        open(5)

        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `another lesson's form opens over the one being edited, which waits under it`() {
        showGraph()
        open(5)

        open(7)

        assertEquals(listOf("Schedule", "form 5", "form 7"), stack())
        onUiThread { navController.popBackStack() }
        assertEquals(listOf("Schedule", "form 5"), stack())
    }

    @Test
    fun `a tap opens the form over a screen that is already pushed`() {
        showGraph()
        onUiThread { navController.navigateToTab(TopDestination.MORE) }
        onUiThread { navController.navigate(Routes.Settings) }

        open(7)

        assertEquals(listOf("Schedule", "More", "Settings", "form 7"), stack())
    }
}
