package com.seblful.klas.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.ui.backup.BackupScreen
import com.seblful.klas.ui.diagnostics.DiagnosticsScreen
import com.seblful.klas.ui.groups.GroupDetailScreen
import com.seblful.klas.ui.groups.GroupFormScreen
import com.seblful.klas.ui.groups.GroupFormViewModel
import com.seblful.klas.ui.homework.HomeworkScreen
import com.seblful.klas.ui.more.AboutScreen
import com.seblful.klas.ui.more.MoreScreen
import com.seblful.klas.ui.people.PeopleScreen
import com.seblful.klas.ui.reports.ReportsScreen
import com.seblful.klas.ui.schedule.ScheduleScreen
import com.seblful.klas.ui.schedule.ScheduleTarget
import com.seblful.klas.ui.schedule.booking.BookingSetupNavigation
import com.seblful.klas.ui.schedule.booking.ClassEditScreen
import com.seblful.klas.ui.schedule.booking.ClassEditViewModel
import com.seblful.klas.ui.settings.AppearanceSettingsScreen
import com.seblful.klas.ui.settings.CalendarSettingsScreen
import com.seblful.klas.ui.settings.LessonSettingsScreen
import com.seblful.klas.ui.settings.NotificationSettingsScreen
import com.seblful.klas.ui.settings.SettingsNavigation
import com.seblful.klas.ui.settings.SettingsScreen
import com.seblful.klas.ui.students.StudentDetailScreen
import com.seblful.klas.ui.students.StudentFormScreen
import com.seblful.klas.ui.students.StudentTask
import com.seblful.klas.ui.subjects.SubjectsScreen
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * Every destination in the app, and where each one leads — the graph the main tabs host.
 *
 * The two arrivals are **read**, not passed: the schedule and the reports read them as they compose, so a
 * tray tap that lands after the graph was built still reaches its tab. See [Arrival].
 */
fun NavGraphBuilder.klasGraph(
    navController: NavHostController,
    scheduleArrival: (String) -> Arrival<ScheduleTarget>?,
    debtsArrival: (String) -> Arrival<Unit>?,
) {
    tabs(navController, scheduleArrival, debtsArrival)
    peopleCards(navController)
    moreScreens(navController)
    screen<Routes.ClassEdit> { entry ->
        val viewModel: ClassEditViewModel = assistedMetroViewModel()
        CreatedResult(entry, CREATED_STUDENT, viewModel::selectCreatedStudent)
        CreatedResult(entry, CREATED_GROUP) { viewModel.choose(Assignee.Group(it)) }
        ClassEditScreen(
            onBack = { navController.popBackStack() },
            viewModel = viewModel,
            setup = BookingSetupNavigation(
                addStudent = { navController.navigate(Routes.StudentAddForSubject()) },
                addGroup = { navController.navigate(Routes.GroupAdd) },
                studentSubjects = { navController.navigate(Routes.StudentDetail(it, task = StudentTask.SUBJECT)) },
                groupEdit = { navController.navigate(Routes.GroupEdit(it)) },
                subjects = { navController.navigate(Routes.Subjects) },
            ),
        )
    }
}

private fun NavGraphBuilder.tabs(
    navController: NavHostController,
    scheduleArrival: (String) -> Arrival<ScheduleTarget>?,
    debtsArrival: (String) -> Arrival<Unit>?,
) {
    screen<Routes.Schedule> { entry ->
        ScheduleScreen(
            arrival = scheduleArrival(entry.id),
            onAddClass = { day -> navController.navigate(Routes.classEdit(0, day = day)) },
            onEditClass = { navController.navigate(Routes.classEdit(it)) },
            // The way out of "nothing can be booked yet": the schedule's empty state offers
            // the missing step rather than only naming it.
            onAddStudent = { navController.navigate(Routes.StudentAdd) },
            onOpenStudent = { navController.navigate(Routes.StudentDetail(it)) },
            onOpenGroup = { navController.navigate(Routes.GroupDetail(it)) },
            onSetStudentHomework = { navController.navigate(Routes.StudentDetail(it, task = StudentTask.HOMEWORK)) },
            onSetGroupHomework = { navController.navigate(Routes.GroupDetail(it, setHomework = true)) },
            onRecordPayment = { navController.navigate(Routes.StudentDetail(it, task = StudentTask.PAYMENT)) },
        )
    }
    screen<Routes.Reports> { entry ->
        ReportsScreen(
            onStudentClick = { navController.navigate(Routes.StudentDetail(it)) },
            debtsArrival = debtsArrival(entry.id),
        )
    }
    screen<Routes.More> {
        MoreScreen(
            onSubjects = { navController.navigate(Routes.Subjects) },
            onBackup = { navController.navigate(Routes.Backup) },
            onSettings = { navController.navigate(Routes.Settings) },
            onAbout = { navController.navigate(Routes.About) },
        )
    }
    screen<Routes.People> {
        PeopleScreen(
            onStudentClick = { navController.navigate(Routes.StudentDetail(it)) },
            onAddStudent = { navController.navigate(Routes.StudentAdd) },
            onGroupClick = { navController.navigate(Routes.GroupDetail(it)) },
            onAddGroup = { navController.navigate(Routes.GroupAdd) },
        )
    }
    screen<Routes.Homework> {
        HomeworkScreen(onAddStudent = { navController.navigate(Routes.StudentAdd) })
    }
}

private fun NavGraphBuilder.peopleCards(navController: NavHostController) {
    screen<Routes.StudentAdd> {
        StudentFormScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.StudentAddForSubject> { entry ->
        StudentFormScreen(
            onBack = { navController.popBackStack() },
            initialSubjectId = entry.toRoute<Routes.StudentAddForSubject>().subject.takeIf { it != 0L },
            onCreated = { navController.returnCreated(CREATED_STUDENT, it) },
        )
    }
    screen<Routes.StudentDetail> { entry ->
        val route = entry.toRoute<Routes.StudentDetail>()
        StudentDetailScreen(
            onBack = { navController.popBackStack() },
            onLessonClick = { navController.navigate(Routes.classEdit(it)) },
            onBookLesson = { navController.navigate(Routes.classEdit(0, studentId = route.id)) },
            initialTask = route.task,
        )
    }

    screen<Routes.GroupAdd> { entry ->
        GroupFormDestination(navController, entry, creating = true)
    }
    screen<Routes.GroupDetail> { entry ->
        val route = entry.toRoute<Routes.GroupDetail>()
        val id = route.id
        GroupDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.GroupEdit(id)) },
            onMemberClick = { navController.navigate(Routes.StudentDetail(it)) },
            onLessonClick = { navController.navigate(Routes.classEdit(it)) },
            onBookLesson = { navController.navigate(Routes.classEdit(0, groupId = id)) },
            initialHomework = route.setHomework,
        )
    }
    // Adding and editing are the same form; it tells them apart by the id it is given.
    screen<Routes.GroupEdit> { entry ->
        GroupFormDestination(navController, entry, creating = false)
    }
}

@Composable
private fun GroupFormDestination(navController: NavHostController, entry: NavBackStackEntry, creating: Boolean) {
    val viewModel: GroupFormViewModel = assistedMetroViewModel()
    CreatedResult(entry, CREATED_STUDENT) { viewModel.toggleMember(it, true) }
    GroupFormScreen(
        onBack = { navController.popBackStack() },
        viewModel = viewModel,
        onAddStudent = { subject -> navController.navigate(Routes.StudentAddForSubject(subject ?: 0)) },
        onSaved = if (creating) ({ navController.returnCreated(CREATED_GROUP, it) }) else null,
    )
}

private const val CREATED_STUDENT = "createdStudent"
private const val CREATED_GROUP = "createdGroup"

private fun NavController.returnCreated(key: String, id: Long) {
    previousBackStackEntry?.savedStateHandle?.set(key, id)
    popBackStack()
}

@Composable
private fun CreatedResult(entry: NavBackStackEntry, key: String, onCreated: (Long) -> Unit) {
    val id by entry.savedStateHandle.getStateFlow(key, 0L).collectAsStateWithLifecycle()
    LaunchedEffect(id) {
        if (id != 0L) {
            onCreated(id)
            entry.savedStateHandle[key] = 0L
        }
    }
}

private fun NavGraphBuilder.moreScreens(navController: NavHostController) {
    screen<Routes.Subjects> {
        SubjectsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.Backup> {
        BackupScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.Settings> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            navigation = SettingsNavigation(
                lessons = { navController.navigate(Routes.LessonSettings) },
                notifications = { navController.navigate(Routes.NotificationSettings) },
                calendar = { navController.navigate(Routes.CalendarSettings) },
                appearance = { navController.navigate(Routes.AppearanceSettings) },
                diagnostics = { navController.navigate(Routes.Diagnostics) },
            ),
        )
    }
    screen<Routes.LessonSettings> {
        LessonSettingsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.NotificationSettings> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.CalendarSettings> {
        CalendarSettingsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.AppearanceSettings> {
        AppearanceSettingsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.Diagnostics> {
        DiagnosticsScreen(onBack = { navController.popBackStack() })
    }
    screen<Routes.About> {
        AboutScreen(
            onBack = { navController.popBackStack() },
            // About is where the app makes the "your data is on this device and nowhere else"
            // promise, which is also where its one obligation is stated — so the way to act on
            // it is here rather than back in the menu.
            onBackup = { navController.navigate(Routes.Backup) },
        )
    }
}

/**
 * Switches to [tab] the way the bar does — back to the start destination with the state
 * saved, so each tab keeps where the tutor was in it. External interruptions use [openTabFromTray]
 * to preserve a pushed form.
 *
 * **The tab already showing is left alone.** Switching to it would pop it and restore the same entry,
 * which re-targets the fade it is arriving with: a second tap inside the first frames of that fade — the
 * slow ones, on a cold or idle app — made the screen lurch forward instead of finishing it.
 *
 * **A screen pushed over the tab asked for is closed.** The tab is already under it, so the pop and restore
 * used to land on that same screen again. A screen pushed over a different tab waits with its tab.
 */
// `currentBackStack` is `@RestrictedApi`, for the reason `AppRoot` gives where it reads the same list.
@SuppressLint("RestrictedApi")
fun NavController.navigateToTab(tab: TopDestination) {
    if (currentDestination?.let(TopDestination::of) == tab) return
    if (owningTab(currentBackStack.value) == tab && popBackStack(tab.route, inclusive = false)) return
    navigate(tab.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** External interruptions keep the current screen and its draft on the stack for Back. */
fun NavController.openTabFromTray(tab: TopDestination) {
    if (currentDestination?.let(TopDestination::of) == null) {
        navigate(tab.route) { launchSingleTop = true }
    } else {
        navigateToTab(tab)
    }
}

/**
 * Opens lesson [classId]'s form for a tray tap, **over whatever is open** — and not twice: a second tap on the
 * same notification finds the form already on top and leaves it.
 */
fun NavController.openLessonForm(classId: Long) {
    val top = currentBackStackEntry
    val alreadyOpen = top?.destination?.hasRoute(Routes.ClassEdit::class) == true &&
        top.toRoute<Routes.ClassEdit>().id == classId
    if (!alreadyOpen) navigate(Routes.classEdit(classId))
}
