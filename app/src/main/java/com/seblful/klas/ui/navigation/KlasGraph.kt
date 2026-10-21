package com.seblful.klas.ui.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import androidx.savedstate.serialization.encodeToSavedState
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.ui.backup.BackupScreen
import com.seblful.klas.ui.common.EmptyState
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
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * Every destination in the app, and where each one leads.
 *
 * The two arrivals are **read**, not passed: the schedule and the reports read them as they compose, so a
 * tray tap that lands after the graph was built still reaches its tab. They are keyed by [Screen.id]. See
 * [Arrival].
 */
class KlasGraph(
    private val navigator: KlasNavigator,
    private val scheduleArrival: (Long) -> Arrival<ScheduleTarget>?,
    private val debtsArrival: (Long) -> Arrival<Unit>?,
) {
    fun entry(screen: Screen): NavEntry<Screen> =
        NavEntry(screen, contentKey = screen.id, metadata = screen.route.paneMetadata()) { Content(screen) }

    private val back: () -> Unit = navigator::goBack

    private val go: (Route) -> Unit = navigator::navigate

    // A dispatch table: one plain branch per route, each a call. Exhaustive over the sealed [Route], so a
    // route without a screen does not compile.
    @Suppress("CyclomaticComplexMethod")
    @Composable
    private fun Content(screen: Screen) {
        when (val route = screen.route) {
            Routes.Schedule -> Schedule(screen.id)
            Routes.Reports -> ReportsScreen(onStudentClick = { go(Routes.StudentDetail(it)) }, debtsArrival(screen.id))
            Routes.More -> More()
            Routes.People -> People()
            Routes.Homework -> HomeworkScreen(onAddStudent = { go(Routes.StudentAdd) })
            Routes.StudentAdd -> StudentFormScreen(onBack = back)
            is Routes.StudentAddForSubject -> StudentAddForSubject(route)
            is Routes.StudentDetail -> StudentDetail(route)
            Routes.GroupAdd -> GroupForm(editing = null)
            is Routes.GroupDetail -> GroupDetail(route)
            is Routes.GroupEdit -> GroupForm(editing = route)
            is Routes.ClassEdit -> ClassEdit(route)
            Routes.Subjects -> SubjectsScreen(onBack = back)
            Routes.Backup -> BackupScreen(onBack = back)
            Routes.Settings -> Settings()
            Routes.LessonSettings -> LessonSettingsScreen(onBack = back)
            Routes.NotificationSettings -> NotificationSettingsScreen(onBack = back)
            Routes.CalendarSettings -> CalendarSettingsScreen(onBack = back)
            Routes.AppearanceSettings -> AppearanceSettingsScreen(onBack = back)
            Routes.Diagnostics -> DiagnosticsScreen(onBack = back)
            // About is where the app makes the "your data is on this device and nowhere else" promise, which
            // is also where its one obligation is stated — so the way to act on it is here.
            Routes.About -> AboutScreen(onBack = back, onBackup = { go(Routes.Backup) })
        }
    }

    @Composable
    private fun Schedule(id: Long) {
        ScheduleScreen(
            arrival = scheduleArrival(id),
            onAddClass = { day -> go(Routes.classEdit(0, day = day)) },
            onEditClass = { go(Routes.classEdit(it)) },
            // The way out of "nothing can be booked yet": the schedule's empty state offers the missing step
            // rather than only naming it.
            onAddStudent = { go(Routes.StudentAdd) },
            onOpenStudent = { go(Routes.StudentDetail(it)) },
            onOpenGroup = { go(Routes.GroupDetail(it)) },
            onSetStudentHomework = { go(Routes.StudentDetail(it, task = StudentTask.HOMEWORK)) },
            onSetGroupHomework = { go(Routes.GroupDetail(it, setHomework = true)) },
            onRecordPayment = { go(Routes.StudentDetail(it, task = StudentTask.PAYMENT)) },
        )
    }

    @Composable
    private fun More() {
        MoreScreen(
            onSubjects = { go(Routes.Subjects) },
            onBackup = { go(Routes.Backup) },
            onSettings = { go(Routes.Settings) },
            onAbout = { go(Routes.About) },
        )
    }

    /** The list of the People pane: a card it opens replaces the one shown beside it. */
    @Composable
    private fun People() {
        PeopleScreen(
            onStudentClick = { navigator.replaceAbove(Routes.People, Routes.StudentDetail(it)) },
            onAddStudent = { go(Routes.StudentAdd) },
            onGroupClick = { navigator.replaceAbove(Routes.People, Routes.GroupDetail(it)) },
            onAddGroup = { go(Routes.GroupAdd) },
            open = openBesideList(navigator.backStack),
        )
    }

    @Composable
    private fun StudentAddForSubject(route: Routes.StudentAddForSubject) {
        val results = LocalResultEventBus.current
        StudentFormScreen(
            onBack = back,
            initialSubjectId = route.subject.takeIf { it != 0L },
            onCreated = {
                results.sendResult(CreatedStudent(it))
                back()
            },
        )
    }

    @Composable
    private fun StudentDetail(route: Routes.StudentDetail) {
        StudentDetailScreen(
            onBack = back,
            onLessonClick = { go(Routes.classEdit(it)) },
            onBookLesson = { go(Routes.classEdit(0, studentId = route.id)) },
            initialTask = route.task,
            viewModel = routeViewModel(route),
            backArrow = !besideList(),
        )
    }

    @Composable
    private fun GroupDetail(route: Routes.GroupDetail) {
        GroupDetailScreen(
            onBack = back,
            onEdit = { go(Routes.GroupEdit(route.id)) },
            onMemberClick = { go(Routes.StudentDetail(it)) },
            onLessonClick = { go(Routes.classEdit(it)) },
            onBookLesson = { go(Routes.classEdit(0, groupId = route.id)) },
            initialHomework = route.setHomework,
            viewModel = routeViewModel(route),
            backArrow = !besideList(),
        )
    }

    /** Adding and editing are the same form; it tells them apart by the id it is given, none for a new one. */
    @Composable
    private fun GroupForm(editing: Routes.GroupEdit?) {
        val viewModel: GroupFormViewModel =
            if (editing != null) routeViewModel(editing) else routeViewModel(Routes.GroupAdd)
        val results = LocalResultEventBus.current
        ResultEffect<CreatedStudent> { viewModel.toggleMember(it.id, true) }
        GroupFormScreen(
            onBack = back,
            viewModel = viewModel,
            onAddStudent = { subject -> go(Routes.StudentAddForSubject(subject ?: 0)) },
            onSaved = if (editing == null) {
                {
                    results.sendResult(CreatedGroup(it))
                    back()
                }
            } else {
                null
            },
        )
    }

    @Composable
    private fun ClassEdit(route: Routes.ClassEdit) {
        val viewModel: ClassEditViewModel = routeViewModel(route)
        ResultEffect<CreatedStudent> { viewModel.selectCreatedStudent(it.id) }
        ResultEffect<CreatedGroup> { viewModel.choose(Assignee.Group(it.id)) }
        ClassEditScreen(
            onBack = back,
            viewModel = viewModel,
            setup = BookingSetupNavigation(
                addStudent = { go(Routes.StudentAddForSubject()) },
                addGroup = { go(Routes.GroupAdd) },
                studentSubjects = { go(Routes.StudentDetail(it, task = StudentTask.SUBJECT)) },
                groupEdit = { go(Routes.GroupEdit(it)) },
                subjects = { go(Routes.Subjects) },
            ),
        )
    }

    @Composable
    private fun Settings() {
        SettingsScreen(
            onBack = back,
            navigation = SettingsNavigation(
                lessons = { go(Routes.LessonSettings) },
                notifications = { go(Routes.NotificationSettings) },
                calendar = { go(Routes.CalendarSettings) },
                appearance = { go(Routes.AppearanceSettings) },
                diagnostics = { go(Routes.Diagnostics) },
            ),
        )
    }
}

/** The card opened from the People list, while it is shown beside the list; null otherwise. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun openBesideList(stack: List<Screen>): Assignee? {
    if (LocalListDetailSceneScope.current == null) return null
    return when (val card = stack.getOrNull(stack.indexOfLast { it.route == Routes.People } + 1)?.route) {
        is Routes.StudentDetail -> Assignee.Student(card.id)
        is Routes.GroupDetail -> Assignee.Group(card.id)
        else -> null
    }
}

/** Whether this card shares the window with the list it was opened from. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun besideList(): Boolean = LocalListDetailSceneScope.current != null

/** A student made by a setup detour, handed back to the form that asked for one. */
private data class CreatedStudent(val id: Long)

/** A group made by a setup detour, handed back to the booking form. */
private data class CreatedGroup(val id: Long)

/**
 * Where a route sits in a list-detail layout: People is the list, and the student and group cards it opens are
 * the detail beside it once the window has room for two panes. Opened from anywhere else, a card has no list
 * under it and fills the window as before.
 */
private enum class Pane { LIST, DETAIL }

private fun Route.pane(): Pane? = when (this) {
    Routes.People -> Pane.LIST
    is Routes.StudentDetail, is Routes.GroupDetail -> Pane.DETAIL
    else -> null
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun Route.paneMetadata(): Map<String, Any> = when (pane()) {
    Pane.LIST -> ListDetailSceneStrategy.listPane(detailPlaceholder = { PickPerson() })
    Pane.DETAIL -> ListDetailSceneStrategy.detailPane()
    null -> emptyMap()
}

/**
 * Whether the top of [backStack] is a card with the list it came from under it — what a two-pane window shows
 * side by side, so the tabs stay with the list.
 */
fun cardBesideList(backStack: List<Screen>): Boolean {
    val list = backStack.indexOfLast { it.route.pane() != Pane.DETAIL }
    return list in 0 until backStack.lastIndex && backStack[list].route.pane() == Pane.LIST
}

@Composable
private fun PickPerson() {
    EmptyState(title = stringResource(R.string.people_pick_card), icon = KlasIcons.Outlined.Group)
}

/**
 * [VM] for a screen whose arguments are [route]'s properties, handed to its `SavedStateHandle` under their
 * names — what `NavHost` did by itself, and Navigation 3 leaves to the app.
 */
@Composable
inline fun <reified VM : ViewModel, reified R : Route> routeViewModel(route: R): VM {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner for $route" }
    val defaults = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    val extras = MutableCreationExtras(defaults).apply { set(DEFAULT_ARGS_KEY, encodeToSavedState(route)) }
    return assistedMetroViewModel(viewModelStoreOwner = owner, extras = extras)
}
