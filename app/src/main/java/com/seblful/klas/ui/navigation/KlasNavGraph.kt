package com.seblful.klas.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.seblful.klas.ui.backup.BackupScreen
import com.seblful.klas.ui.diagnostics.DiagnosticsScreen
import com.seblful.klas.ui.groups.GroupDetailScreen
import com.seblful.klas.ui.groups.GroupFormScreen
import com.seblful.klas.ui.homework.HomeworkScreen
import com.seblful.klas.ui.more.AboutScreen
import com.seblful.klas.ui.more.MoreScreen
import com.seblful.klas.ui.more.SettingsScreen
import com.seblful.klas.ui.people.PeopleScreen
import com.seblful.klas.ui.reports.ReportsScreen
import com.seblful.klas.ui.schedule.ScheduleScreen
import com.seblful.klas.ui.schedule.ScheduleTarget
import com.seblful.klas.ui.schedule.booking.ClassEditScreen
import com.seblful.klas.ui.students.StudentDetailScreen
import com.seblful.klas.ui.students.StudentFormScreen
import com.seblful.klas.ui.subjects.SubjectsScreen

/**
 * Every destination in the app, and where each one leads — the graph the main tabs host.
 *
 * The two arrivals are **read**, not passed: the schedule and the reports read them as they compose, so a
 * tray tap that lands after the graph was built still reaches its tab. See [Arrival].
 */
fun NavGraphBuilder.klasGraph(
    navController: NavHostController,
    scheduleArrival: () -> Arrival<ScheduleTarget>?,
    debtsArrival: () -> Arrival<Unit>?,
) {
    tabs(navController, scheduleArrival, debtsArrival)
    peopleCards(navController)
    moreScreens(navController)
    screen(
        Routes.CLASS_EDIT,
        arguments = listOf(
            IdArgument,
            navArgument("group") { type = NavType.LongType; defaultValue = 0L },
            navArgument("day") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        ClassEditScreen(onBack = { navController.popBackStack() })
    }
}

/** The five tabs, and where each of them leads. */
private fun NavGraphBuilder.tabs(
    navController: NavHostController,
    scheduleArrival: () -> Arrival<ScheduleTarget>?,
    debtsArrival: () -> Arrival<Unit>?,
) {
    screen(Routes.SCHEDULE) {
        ScheduleScreen(
            arrival = scheduleArrival(),
            onAddClass = { day -> navController.navigate(Routes.classEdit(0, day = day)) },
            onEditClass = { navController.navigate(Routes.classEdit(it)) },
            // The way out of "nothing can be booked yet": the schedule's empty state offers
            // the missing step rather than only naming it.
            onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
        )
    }
    screen(Routes.REPORTS) {
        ReportsScreen(
            onStudentClick = { navController.navigate(Routes.studentDetail(it)) },
            debtsArrival = debtsArrival(),
        )
    }
    screen(Routes.MORE) {
        MoreScreen(
            onSubjects = { navController.navigate(Routes.SUBJECTS) },
            onBackup = { navController.navigate(Routes.BACKUP) },
            onSettings = { navController.navigate(Routes.SETTINGS) },
            onAbout = { navController.navigate(Routes.ABOUT) },
        )
    }
    screen(Routes.PEOPLE) {
        PeopleScreen(
            onStudentClick = { navController.navigate(Routes.studentDetail(it)) },
            onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
            onGroupClick = { navController.navigate(Routes.groupDetail(it)) },
            onAddGroup = { navController.navigate(Routes.GROUP_ADD) },
        )
    }
    screen(Routes.HOMEWORK) {
        HomeworkScreen(onAddStudent = { navController.navigate(Routes.STUDENT_ADD) })
    }
}

/** A student's and a group's card and form. */
private fun NavGraphBuilder.peopleCards(navController: NavHostController) {
    screen(Routes.STUDENT_ADD) {
        StudentFormScreen(onBack = { navController.popBackStack() })
    }
    screen(
        Routes.STUDENT_DETAIL,
        arguments = listOf(IdArgument),
    ) {
        StudentDetailScreen(
            onBack = { navController.popBackStack() },
            onLessonClick = { navController.navigate(Routes.classEdit(it)) },
        )
    }

    // Declared before the `group/{id}` card so the literal route can never be read as an id.
    screen(Routes.GROUP_ADD) {
        GroupFormScreen(onBack = { navController.popBackStack() })
    }
    screen(
        Routes.GROUP_DETAIL,
        arguments = listOf(IdArgument),
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
    screen(
        Routes.GROUP_EDIT,
        arguments = listOf(IdArgument),
    ) {
        GroupFormScreen(onBack = { navController.popBackStack() })
    }
}

/** What «Ещё» opens: subjects, backup, settings and what settings opens, about. */
private fun NavGraphBuilder.moreScreens(navController: NavHostController) {
    screen(Routes.SUBJECTS) {
        SubjectsScreen(onBack = { navController.popBackStack() })
    }
    screen(Routes.BACKUP) {
        BackupScreen(onBack = { navController.popBackStack() })
    }
    screen(Routes.SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            // Diagnostics is developer furniture and used to sit as a peer of «Настройки» in
            // «Ещё». It is reached from the foot of Settings now, where service tools belong.
            onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
        )
    }
    screen(Routes.DIAGNOSTICS) {
        DiagnosticsScreen(onBack = { navController.popBackStack() })
    }
    screen(Routes.ABOUT) {
        AboutScreen(
            onBack = { navController.popBackStack() },
            // About is where the app makes the "your data is on this device and nowhere else"
            // promise, which is also where its one obligation is stated — so the way to act on
            // it is here rather than back in the menu.
            onBackup = { navController.navigate(Routes.BACKUP) },
        )
    }
}

/**
 * Switches to the tab at [route] the way the bar does — back to the start destination with the state
 * saved, so each tab keeps where the tutor was in it rather than stacking one tab on another. The bar and
 * a tray tap that names a tab both switch this way; they used to spell it out once each.
 */
fun NavController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** The `{id}` every card and form route carries. */
private val IdArgument = navArgument("id") { type = NavType.LongType }
