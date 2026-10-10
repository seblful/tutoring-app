package com.seblful.klas.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the bar's tap does, on the real graph builder and the real `navigateToTab`: the tab already showing
 * is left alone, and everything else still switches.
 *
 * The fade a re-tap used to disturb is pinned on pixels by `TabReselectFadeTest` (a device test); this is
 * the contract around the guard, where a mistake would not be a glitch but a tab that cannot be reached.
 */
@RunWith(RobolectricTestRunner::class)
class NavigateToTabTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    /** The route of every destination the controller has arrived at, in order. */
    private val arrivals = mutableListOf<String?>()

    private fun showTabs() {
        compose.setContent {
            navController = rememberNavController()
            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    arrivals += destination.route
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose { navController.removeOnDestinationChangedListener(listener) }
            }
            NavHost(navController, startDestination = Routes.SCHEDULE) {
                TopDestination.entries.forEach { tab -> screen(tab.route) {} }
                screen(Routes.SUBJECTS) {}
            }
        }
    }

    private fun tap(tab: TopDestination) = onUiThread { navController.navigateToTab(tab.route) }

    private fun onUiThread(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
    }

    @Test
    fun `a tap on the tab that is showing goes nowhere`() {
        showTabs()
        tap(TopDestination.HOMEWORK)
        val arrived = arrivals.size

        tap(TopDestination.HOMEWORK)
        tap(TopDestination.HOMEWORK)

        assertEquals(arrived, arrivals.size)
        assertEquals(Routes.HOMEWORK, navController.currentDestination?.route)
    }

    @Test
    fun `a tap on the start tab while it shows goes nowhere either`() {
        showTabs()
        val arrived = arrivals.size

        tap(TopDestination.SCHEDULE)

        assertEquals(arrived, arrivals.size)
    }

    @Test
    fun `a tap on another tab switches to it`() {
        showTabs()

        tap(TopDestination.HOMEWORK)
        assertEquals(Routes.HOMEWORK, navController.currentDestination?.route)
        tap(TopDestination.PEOPLE)
        assertEquals(Routes.PEOPLE, navController.currentDestination?.route)
        tap(TopDestination.HOMEWORK)
        assertEquals(Routes.HOMEWORK, navController.currentDestination?.route)
    }
}
