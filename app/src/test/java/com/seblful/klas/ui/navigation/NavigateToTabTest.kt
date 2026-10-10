package com.seblful.klas.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
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
 * What switching to a tab does, on the real graph builder and the real `navigateToTab`: the tab already
 * showing is left alone, everything else still switches, and a screen pushed over the tab asked for is
 * closed by it — where one pushed over another tab waits with that tab.
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
                screen(Routes.STUDENT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {}
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
    }

    /** What is on the back stack, bottom first, by route — the screens pushed over a tab by what they are. */
    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { entry ->
        entry.destination.route?.substringBefore('?')?.replace("/{id}", "")
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

    /** The tray's case: «Сегодня» tapped while a booking form, opened from the schedule, is on top of it. */
    @Test
    fun `a tap on the schedule closes a form opened from it`() {
        showTabs()
        onUiThread { navController.navigate(Routes.classEdit(5)) }

        tap(TopDestination.SCHEDULE)

        assertEquals(listOf(Routes.SCHEDULE), stack())
    }

    /** The debt digest, tapped while a student's card, opened from the reports, is on top of them. */
    @Test
    fun `a tap on the reports closes a card opened from them`() {
        showTabs()
        tap(TopDestination.REPORTS)
        onUiThread { navController.navigate(Routes.studentDetail(1)) }

        tap(TopDestination.REPORTS)

        assertEquals(listOf(Routes.SCHEDULE, Routes.REPORTS), stack())
        assertEquals(Routes.REPORTS, navController.currentDestination?.route)
    }

    /** What did not need fixing, kept as it was: a screen over *another* tab waits with that tab. */
    @Test
    fun `a screen pushed over another tab waits with it and comes back with it`() {
        showTabs()
        tap(TopDestination.PEOPLE)
        onUiThread { navController.navigate(Routes.studentDetail(1)) }

        tap(TopDestination.SCHEDULE)
        assertEquals(listOf(Routes.SCHEDULE), stack())

        tap(TopDestination.PEOPLE)
        assertEquals(listOf(Routes.SCHEDULE, Routes.PEOPLE, "student"), stack())
    }
}
