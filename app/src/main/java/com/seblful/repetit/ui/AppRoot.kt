package com.seblful.repetit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.CompositionLocalProvider
import com.seblful.repetit.ui.common.LocalCurrency
import com.seblful.repetit.ui.theme.TutoringAppTheme
import com.seblful.repetit.ui.backup.BackupScreen
import com.seblful.repetit.ui.diagnostics.DiagnosticsScreen
import com.seblful.repetit.ui.more.MoreScreen
import com.seblful.repetit.ui.more.SettingsScreen
import com.seblful.repetit.ui.navigation.Routes
import com.seblful.repetit.ui.navigation.TopDestination
import com.seblful.repetit.ui.onboarding.OnboardingScreen
import com.seblful.repetit.ui.reports.ReportsScreen
import com.seblful.repetit.ui.schedule.ClassEditScreen
import com.seblful.repetit.ui.schedule.ScheduleScreen
import com.seblful.repetit.ui.students.StudentDetailScreen
import com.seblful.repetit.ui.students.StudentFormScreen
import com.seblful.repetit.ui.students.StudentsScreen
import com.seblful.repetit.ui.subjects.SubjectsScreen

@Composable
fun AppRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val settings by rootViewModel.settings.collectAsState()

    val darkTheme = when (settings?.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    TutoringAppTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalCurrency provides (settings?.currency ?: "BYN")) {
            when {
                settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !settings!!.onboardingComplete ->
                    OnboardingScreen(onFinished = { /* settings flip re-renders into MainApp */ })
                else -> MainApp()
            }
        }
    }
}

@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopDestination.routes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
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
                    onAddClass = { date -> navController.navigate(Routes.classEdit(0, date.toEpochDay())) },
                    onEditClass = { navController.navigate(Routes.classEdit(it)) },
                )
            }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onSubjects = { navController.navigate(Routes.SUBJECTS) },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }

            composable(Routes.STUDENTS) {
                StudentsScreen(
                    onStudentClick = { navController.navigate(Routes.studentDetail(it)) },
                    onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
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

            composable(Routes.SUBJECTS) {
                SubjectsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Routes.CLASS_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                ClassEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
