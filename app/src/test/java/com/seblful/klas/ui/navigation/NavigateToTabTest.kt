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

@RunWith(RobolectricTestRunner::class)
class NavigateToTabTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

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
                screen<Routes.Settings> {}
                screen<Routes.LessonSettings> {}
                screen<Routes.NotificationSettings> {}
                screen<Routes.CalendarSettings> {}
                screen<Routes.AppearanceSettings> {}
            }
        }
    }

    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { it.destination.routeName() }

    private fun showing(): TopDestination? = navController.currentDestination?.let(TopDestination::of)

    private fun tap(tab: TopDestination) = onUiThread { navController.navigateToTab(tab) }

    private fun onUiThread(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
    }

    @Test
    fun `tray arrivals preserve an unfinished form from the same tab and another tab`() {
        showTabs()
        onUiThread { navController.navigate(Routes.classEdit(0)) }
        val draft = navController.currentBackStackEntry!!
        onUiThread { draft.savedStateHandle["draft"] = "Maths at 30" }

        for (tab in listOf(TopDestination.SCHEDULE, TopDestination.REPORTS)) {
            onUiThread { navController.openTabFromTray(tab) }
            assertEquals(tab, showing())
            onUiThread { navController.popBackStack() }
            assertEquals(draft, navController.currentBackStackEntry)
            assertEquals("Maths at 30", draft.savedStateHandle.get<String>("draft"))
        }
    }

    @Test
    fun `settings categories return through the index and retain More tab ownership`() {
        showTabs()
        tap(TopDestination.MORE)
        onUiThread { navController.navigate(Routes.Settings) }
        for (category in listOf(
            Routes.LessonSettings, Routes.NotificationSettings, Routes.CalendarSettings, Routes.AppearanceSettings,
        )) {
            onUiThread { navController.navigate(category) }
            assertEquals(TopDestination.MORE, owningTab(navController.currentBackStack.value))
            onUiThread { navController.popBackStack() }
            assertEquals(listOf("Schedule", "More", "Settings"), stack())
        }
        onUiThread { navController.navigate(Routes.CalendarSettings) }
        tap(TopDestination.SCHEDULE)
        tap(TopDestination.MORE)
        assertEquals(listOf("Schedule", "More", "Settings", "CalendarSettings"), stack())
        tap(TopDestination.MORE)
        assertEquals(listOf("Schedule", "More"), stack())
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

    @Test
    fun `a tap on the schedule closes a form opened from it`() {
        showTabs()
        onUiThread { navController.navigate(Routes.classEdit(5)) }

        tap(TopDestination.SCHEDULE)

        assertEquals(listOf("Schedule"), stack())
    }

    @Test
    fun `a tap on the reports closes a card opened from them`() {
        showTabs()
        tap(TopDestination.REPORTS)
        onUiThread { navController.navigate(Routes.StudentDetail(1)) }

        tap(TopDestination.REPORTS)

        assertEquals(listOf("Schedule", "Reports"), stack())
        assertEquals(TopDestination.REPORTS, showing())
    }

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
