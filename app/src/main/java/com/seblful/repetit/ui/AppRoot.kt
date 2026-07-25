package com.seblful.repetit.ui

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
import com.seblful.repetit.ui.common.LocalCurrency
import com.seblful.repetit.ui.theme.TutoringAppTheme
import com.seblful.repetit.ui.backup.BackupScreen
import com.seblful.repetit.ui.diagnostics.DiagnosticsScreen
import com.seblful.repetit.ui.groups.GroupFormScreen
import com.seblful.repetit.ui.groups.GroupsScreen
import com.seblful.repetit.ui.more.MoreScreen
import com.seblful.repetit.ui.more.SettingsScreen
import com.seblful.repetit.ui.navigation.BottomBar
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
                else -> MainApp(showGroupsTab = settings!!.showGroupsTab)
            }
        }
    }
}

/**
 * The main tabs and the whole navigation graph.
 *
 * @param showGroupsTab whether «Группы» is one of the tabs. When it isn't, the section keeps its
 *   route and is reached from «Ещё» instead — so it reads as an ordinary sub-screen there, with a
 *   back arrow and no bottom bar.
 */
@Composable
private fun MainApp(showGroupsTab: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = TopDestination.visible(showGroupsTab)
    val showBottomBar = currentRoute in TopDestination.routes(showGroupsTab)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(tabs = tabs, currentRoute = currentRoute) { dest ->
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
                    onAddClass = { date -> navController.navigate(Routes.classEdit(0, date.toEpochDay())) },
                    onEditClass = { navController.navigate(Routes.classEdit(it)) },
                )
            }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onSubjects = { navController.navigate(Routes.SUBJECTS) },
                    // Listed here only while it has no tab of its own, so the entry point moves
                    // rather than being duplicated — and hiding the tab never hides the groups.
                    onGroups = if (showGroupsTab) null else {
                        { navController.navigate(Routes.GROUPS) }
                    },
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

            composable(Routes.GROUPS) {
                GroupsScreen(
                    // A tab has nowhere to go back to; opened from «Ещё» it does.
                    onBack = if (showGroupsTab) null else {
                        { navController.popBackStack() }
                    },
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) },
                    onAddGroup = { navController.navigate(Routes.GROUP_ADD) },
                )
            }
            // Both routes are the same form.
            composable(Routes.GROUP_ADD) {
                GroupFormScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.GROUP_DETAIL,
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
                    navArgument("group") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                ClassEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
