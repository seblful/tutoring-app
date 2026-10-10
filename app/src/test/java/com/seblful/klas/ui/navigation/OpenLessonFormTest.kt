package com.seblful.klas.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a tap on «Перенести» does to what is already open, on the real route and `openLessonForm`.
 *
 * The lesson form asks before it is left with something typed into it, but only when it is left with
 * Back — a tap in the tray goes round that. So the form being edited must not be *replaced*: it has to
 * wait under the one the tray named, where Back finds it again with what was typed.
 */
@RunWith(RobolectricTestRunner::class)
class OpenLessonFormTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun showGraph() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Routes.SCHEDULE) {
                TopDestination.entries.forEach { tab -> screen(tab.route) {} }
                screen(Routes.SETTINGS) {}
                screen(
                    Routes.CLASS_EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("group") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("day") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {}
            }
        }
        compose.waitForIdle()
    }

    private fun onUiThread(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
    }

    private fun open(classId: Long) = onUiThread { navController.openLessonForm(classId) }

    /** The forms open, bottom first, as the lesson each is for — everything else by its route. */
    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { entry ->
        val route = entry.destination.route?.substringBefore('?') ?: return@mapNotNull null
        if (route == "class/{id}") "form ${entry.arguments?.getLong("id")}" else route
    }

    @Test
    fun `a tap opens the lesson's form`() {
        showGraph()

        open(5)

        assertEquals(listOf(Routes.SCHEDULE, "form 5"), stack())
    }

    @Test
    fun `a second tap on the same notification finds the form already open`() {
        showGraph()

        open(5)
        open(5)

        assertEquals(listOf(Routes.SCHEDULE, "form 5"), stack())
    }

    @Test
    fun `another lesson's form opens over the one being edited, which waits under it`() {
        showGraph()
        open(5)

        open(7)

        assertEquals(listOf(Routes.SCHEDULE, "form 5", "form 7"), stack())
        onUiThread { navController.popBackStack() }
        assertEquals(listOf(Routes.SCHEDULE, "form 5"), stack())
    }

    @Test
    fun `a tap opens the form over a screen that is already pushed`() {
        showGraph()
        onUiThread { navController.navigateToTab(Routes.MORE) }
        onUiThread { navController.navigate(Routes.SETTINGS) }

        open(7)

        assertEquals(listOf(Routes.SCHEDULE, Routes.MORE, Routes.SETTINGS, "form 7"), stack())
    }
}
