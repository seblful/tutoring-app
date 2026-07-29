package com.seblful.klas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.CompositionLocalProvider
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.theme.KlasTheme
import com.seblful.klas.ui.backup.BackupScreen
import com.seblful.klas.ui.diagnostics.DiagnosticsScreen
import com.seblful.klas.ui.groups.GroupDetailScreen
import com.seblful.klas.ui.groups.GroupFormScreen
import com.seblful.klas.ui.more.AboutScreen
import com.seblful.klas.ui.more.MoreScreen
import com.seblful.klas.ui.more.SettingsScreen
import com.seblful.klas.ui.navigation.BottomBar
import com.seblful.klas.ui.navigation.Routes
import com.seblful.klas.ui.navigation.TopDestination
import com.seblful.klas.ui.onboarding.OnboardingScreen
import com.seblful.klas.ui.people.PeopleScreen
import com.seblful.klas.ui.reports.ReportsScreen
import com.seblful.klas.ui.schedule.ClassEditScreen
import com.seblful.klas.ui.schedule.ScheduleScreen
import com.seblful.klas.ui.students.StudentDetailScreen
import com.seblful.klas.ui.students.StudentFormScreen
import com.seblful.klas.ui.subjects.SubjectsScreen

@Composable
fun AppRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    // Read into a plain local rather than a `by` delegate: a delegated property is a getter call each
    // time it is read, so Kotlin cannot smart-cast it and the branches below had to re-assert with
    // `settings!!` what `settings == null` had just ruled out.
    val settings = rootViewModel.settings.collectAsState().value

    // Reading the stored string is [ThemeChoice]'s; only resolving «follow the system» is this
    // composable's, because that answer comes from the platform.
    val darkTheme = when (ThemeChoice.parse(settings?.themeMode)) {
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }

    KlasTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalCurrency provides (settings?.currency ?: Currency.BYN)) {
            when (rootViewModel.routeFor(settings)) {
                RootRoute.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                RootRoute.ONBOARDING ->
                    OnboardingScreen(onFinished = { /* settings flip re-renders into MainApp */ })
                RootRoute.MAIN -> MainApp()
            }
        }
    }
}

/** The main tabs and the whole navigation graph. */
@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopDestination.routes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(tabs = TopDestination.entries, currentRoute = currentRoute) { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SCHEDULE,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.SCHEDULE) {
                ScheduleScreen(
                    onAddClass = { navController.navigate(Routes.classEdit(0)) },
                    onEditClass = { navController.navigate(Routes.classEdit(it)) },
                    // The way out of "nothing can be booked yet": the schedule's empty state offers
                    // the missing step rather than only naming it.
                    onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen(onStudentClick = { navController.navigate(Routes.studentDetail(it)) })
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onSubjects = { navController.navigate(Routes.SUBJECTS) },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onStudentClick = { navController.navigate(Routes.studentDetail(it)) },
                    onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) },
                    onAddGroup = { navController.navigate(Routes.GROUP_ADD) },
                )
            }
            composable(Routes.STUDENT_ADD) {
                StudentFormScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.STUDENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                StudentDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // Declared before the `group/{id}` card so the literal route can never be read as an id.
            composable(Routes.GROUP_ADD) {
                GroupFormScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.GROUP_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                GroupDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.groupEdit(id)) },
                    onMemberClick = { navController.navigate(Routes.studentDetail(it)) },
                    onLessonClick = { navController.navigate(Routes.classEdit(it)) },
                )
            }
            // Adding and editing are the same form; it tells them apart by the id it is given.
            composable(
                Routes.GROUP_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                GroupFormScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SUBJECTS) {
                SubjectsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    // Diagnostics is developer furniture and used to sit as a peer of «Настройки» in
                    // «Ещё». It is reached from the foot of Settings now, where service tools belong.
                    onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Routes.CLASS_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("group") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                ClassEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
