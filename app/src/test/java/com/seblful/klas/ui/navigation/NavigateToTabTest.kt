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
            NavHost(navController, startDestination = Routes.Schedule) {
                TopDestination.entries.forEach { tab -> screen(tab.route::class) {} }
                screen<Routes.StudentDetail> {}
                screen<Routes.ClassEdit> {}
            }
        }
    }

    /** What is on the back stack, bottom first, by route — the screens pushed over a tab by what they are. */
    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { it.destination.routeName() }

    /** The tab showing, or null for a screen pushed over one. */
    private fun showing(): TopDestination? = navController.currentDestination?.let(TopDestination::of)

    private fun tap(tab: TopDestination) = onUiThread { navController.navigateToTab(tab) }

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
        assertEquals(TopDestination.HOMEWORK, showing())
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
        assertEquals(TopDestination.HOMEWORK, showing())
        tap(TopDestination.PEOPLE)
        assertEquals(TopDestination.PEOPLE, showing())
        tap(TopDestination.HOMEWORK)
        assertEquals(TopDestination.HOMEWORK, showing())
    }

    /** The tray's case: «Сегодня» tapped while a booking form, opened from the schedule, is on top of it. */
    @Test
    fun `a tap on the schedule closes a form opened from it`() {
        showTabs()
        onUiThread { navController.navigate(Routes.classEdit(5)) }

        tap(TopDestination.SCHEDULE)

        assertEquals(listOf("Schedule"), stack())
    }

    /** The debt digest, tapped while a student's card, opened from the reports, is on top of them. */
    @Test
    fun `a tap on the reports closes a card opened from them`() {
        showTabs()
        tap(TopDestination.REPORTS)
        onUiThread { navController.navigate(Routes.StudentDetail(1)) }

        tap(TopDestination.REPORTS)

        assertEquals(listOf("Schedule", "Reports"), stack())
        assertEquals(TopDestination.REPORTS, showing())
    }

    /** What did not need fixing, kept as it was: a screen over *another* tab waits with that tab. */
    @Test
    fun `a screen pushed over another tab waits with it and comes back with it`() {
        showTabs()
        tap(TopDestination.PEOPLE)
        onUiThread { navController.navigate(Routes.StudentDetail(1)) }

        tap(TopDestination.SCHEDULE)
        assertEquals(listOf("Schedule"), stack())

        tap(TopDestination.PEOPLE)
        assertEquals(listOf("Schedule", "People", "StudentDetail"), stack())
    }
}
