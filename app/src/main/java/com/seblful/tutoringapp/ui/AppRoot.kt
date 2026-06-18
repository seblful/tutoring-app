package com.seblful.tutoringapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seblful.tutoringapp.ui.backup.BackupScreen
import com.seblful.tutoringapp.ui.more.MoreScreen
import com.seblful.tutoringapp.ui.navigation.Routes
import com.seblful.tutoringapp.ui.navigation.TopDestination
import com.seblful.tutoringapp.ui.reports.ReportsScreen
import com.seblful.tutoringapp.ui.schedule.ClassEditScreen
import com.seblful.tutoringapp.ui.schedule.ScheduleScreen
import com.seblful.tutoringapp.ui.students.StudentDetailScreen
import com.seblful.tutoringapp.ui.students.StudentsScreen
import com.seblful.tutoringapp.ui.subjects.SubjectsScreen

@Composable
fun AppRoot() {
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
                )
            }

            composable(Routes.STUDENTS) {
                StudentsScreen(onStudentClick = { navController.navigate(Routes.studentDetail(it)) })
            }
            composable(
                Routes.STUDENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                StudentDetailScreen(
                    onBack = { navController.popBackStack() },
                    onClassClick = { navController.navigate(Routes.classEdit(it)) },
                )
            }

            composable(Routes.SUBJECTS) {
                SubjectsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
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

