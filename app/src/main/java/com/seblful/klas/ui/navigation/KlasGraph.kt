package com.seblful.klas.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.seblful.klas.ui.groups.GroupListScreen
import com.seblful.klas.ui.homework.HomeworkScreen
import com.seblful.klas.ui.more.AboutScreen
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
import com.seblful.klas.ui.settings.PrivacySettingsScreen
import com.seblful.klas.ui.settings.SettingsCategory
import com.seblful.klas.ui.settings.SettingsNavigation
import com.seblful.klas.ui.settings.SettingsScreen
import com.seblful.klas.ui.students.StudentDetailScreen
import com.seblful.klas.ui.students.StudentFormScreen
import com.seblful.klas.ui.students.StudentListScreen
import com.seblful.klas.ui.students.StudentTask
import com.seblful.klas.ui.subjects.SubjectsScreen
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.today.TodayScreen
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
    /** Asks the schedule just opened for [ScheduleTarget], as a tray tap does — Today's rows lead there. */
    private val arriveAtSchedule: (ScheduleTarget) -> Unit = {},
    /** Asks the reports just opened for their debts. */
    private val arriveAtDebts: () -> Unit = {},
) {
    fun entry(screen: Screen): NavEntry<Screen> =
        NavEntry(screen, contentKey = screen.id, metadata = screen.route.paneMetadata()) { Content(screen) }

    private val go: (Route) -> Unit = navigator::navigate

    // A dispatch table: one plain branch per route, each a call. Exhaustive over the sealed [Route], so a
    // route without a screen does not compile.
    @Suppress("CyclomaticComplexMethod")
    @Composable
    private fun Content(screen: Screen) {
        // This screen's own back, not "whatever is on top": see [KlasNavigator.close].
        val back = { navigator.close(screen) }
        when (val route = screen.route) {
            Routes.Today -> Today()
            Routes.Schedule -> Schedule(screen.id)
            Routes.Reports -> ReportsScreen(
                onStudentClick = { go(Routes.StudentDetail(it)) },
                debtsArrival(screen.id),
                onSettings = settings,
            )
            Routes.People -> People()
            Routes.Homework -> HomeworkScreen(onAddStudent = { go(Routes.StudentAdd) }, onSettings = settings)
            Routes.StudentAdd -> StudentFormScreen(onBack = back)
            is Routes.StudentAddForSubject -> StudentAddForSubject(route, back)
            is Routes.StudentDetail -> StudentDetail(route, back)
            is Routes.StudentEdit -> StudentFormScreen(onBack = back, studentId = route.id)
            is Routes.StudentList -> StudentListScreen(
                section = route.section,
                onBack = back,
                onLessonClick = { go(Routes.classEdit(it)) },
                viewModel = routeViewModel(route),
            )
            Routes.GroupAdd -> GroupForm(editing = null, back)
            is Routes.GroupDetail -> GroupDetail(route, back)
            is Routes.GroupList -> GroupListScreen(
                section = route.section,
                onBack = back,
                onLessonClick = { go(Routes.classEdit(it)) },
                viewModel = routeViewModel(route),
            )
            is Routes.GroupEdit -> GroupForm(editing = route, back)
            is Routes.ClassEdit -> ClassEdit(route, back)
            Routes.Subjects -> SubjectsScreen(onBack = back)
            Routes.Backup -> BackupScreen(onBack = back)
            Routes.Settings -> Settings()
            Routes.LessonSettings -> LessonSettingsScreen(onBack = back.takeUnless { besideList() })
            Routes.NotificationSettings -> NotificationSettingsScreen(onBack = back.takeUnless { besideList() })
            Routes.CalendarSettings -> CalendarSettingsScreen(onBack = back.takeUnless { besideList() })
            Routes.AppearanceSettings -> AppearanceSettingsScreen(onBack = back.takeUnless { besideList() })
            Routes.PrivacySettings -> PrivacySettingsScreen(onBack = back.takeUnless { besideList() })
            Routes.Diagnostics -> DiagnosticsScreen(onBack = back.takeUnless { besideList() })
            // About is where the app makes the "your data is on this device and nowhere else" promise, which
            // is also where its one obligation is stated — so the way to act on it is here.
            Routes.About -> AboutScreen(onBack = back, onBackup = { go(Routes.Backup) })
        }
    }

    @Composable
    private fun Today() {
        TodayScreen(
            onSettings = settings,
            onOpenLesson = {
                navigator.openTabFromTray(TopDestination.SCHEDULE)
                arriveAtSchedule(ScheduleTarget.Lesson(it))
            },
            onBook = { go(Routes.classEdit(0)) },
            onDebts = {
                navigator.openTabFromTray(TopDestination.REPORTS)
                arriveAtDebts()
            },
            onHomework = { navigator.navigateToTab(TopDestination.HOMEWORK) },
            onBackup = { go(Routes.Backup) },
            onSchedule = { navigator.navigateToTab(TopDestination.SCHEDULE) },
            onOpenStudent = { go(Routes.StudentDetail(it)) },
            onRecordPayment = { go(Routes.StudentDetail(it, task = StudentTask.PAYMENT)) },
            onGiveHomework = { go(Routes.StudentDetail(it, task = StudentTask.HOMEWORK)) },
            onPeople = { navigator.navigateToTab(TopDestination.PEOPLE) },
            onReminders = { go(Routes.NotificationSettings) },
            onCalendar = { go(Routes.CalendarSettings) },
        )
    }

    @Composable
    private fun Schedule(id: Long) {
        ScheduleScreen(
            arrival = scheduleArrival(id),
            onAddClass = { day -> go(Routes.classEdit(0, day = day)) },
            onEditClass = { go(Routes.classEdit(it)) },
            onBookLike = { go(Routes.classEdit(0, like = it)) },
            // The way out of "nothing can be booked yet": the schedule's empty state offers the missing step
            // rather than only naming it.
            onAddStudent = { go(Routes.StudentAdd) },
            onOpenStudent = { go(Routes.StudentDetail(it)) },
            onOpenGroup = { go(Routes.GroupDetail(it)) },
            onSetStudentHomework = { go(Routes.StudentDetail(it, task = StudentTask.HOMEWORK)) },
            onSetGroupHomework = { go(Routes.GroupDetail(it, setHomework = true)) },
            onRecordPayment = { go(Routes.StudentDetail(it, task = StudentTask.PAYMENT)) },
            onSettings = settings,
        )
    }

    /** The gear on every tab's bar: Settings, pushed over the tab it was opened from. */
    private val settings: () -> Unit = { navigator.openOnTop(Routes.Settings) }

    /** The list of the People pane: a card it opens replaces the one shown beside it. */
    @Composable
    private fun People() {
        PeopleScreen(
            onStudentClick = { navigator.replaceAbove(Routes.People, Routes.StudentDetail(it)) },
            onAddStudent = { go(Routes.StudentAdd) },
            onGroupClick = { navigator.replaceAbove(Routes.People, Routes.GroupDetail(it)) },
            onAddGroup = { go(Routes.GroupAdd) },
            onSettings = settings,
            open = when (val card = openBeside(Routes.People, navigator.backStack)) {
                is Routes.StudentDetail -> Assignee.Student(card.id)
                is Routes.GroupDetail -> Assignee.Group(card.id)
                else -> null
            },
        )
    }

    @Composable
    private fun StudentAddForSubject(route: Routes.StudentAddForSubject, back: () -> Unit) {
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
    private fun StudentDetail(route: Routes.StudentDetail, back: () -> Unit) {
        StudentDetailScreen(
            onBack = back,
            onLessonClick = { go(Routes.classEdit(it)) },
            onBookLesson = { go(Routes.classEdit(0, studentId = route.id)) },
            onSeeAll = { go(Routes.StudentList(route.id, it)) },
            initialTask = route.task,
            viewModel = routeViewModel(route),
            backArrow = !besideList(),
            onEdit = { go(Routes.StudentEdit(route.id)) },
        )
    }

    @Composable
    private fun GroupDetail(route: Routes.GroupDetail, back: () -> Unit) {
        GroupDetailScreen(
            onBack = back,
            onEdit = { go(Routes.GroupEdit(route.id)) },
            onMemberClick = { go(Routes.StudentDetail(it)) },
            onLessonClick = { go(Routes.classEdit(it)) },
            onBookLesson = { go(Routes.classEdit(0, groupId = route.id)) },
            onSeeAll = { go(Routes.GroupList(route.id, it)) },
            initialHomework = route.setHomework,
            viewModel = routeViewModel(route),
            backArrow = !besideList(),
        )
    }

    /** Adding and editing are the same form; it tells them apart by the id it is given, none for a new one. */
    @Composable
    private fun GroupForm(editing: Routes.GroupEdit?, back: () -> Unit) {
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
    private fun ClassEdit(route: Routes.ClassEdit, back: () -> Unit) {
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

    /** The list of the Settings pane: a page it opens replaces the one shown beside it. */
    @Composable
    private fun Settings() {
        val open = openBeside(Routes.Settings, navigator.backStack)
        val page: (SettingsCategory) -> Unit = { navigator.replaceAbove(Routes.Settings, SettingsPages.getValue(it)) }
        SettingsScreen(
            onBack = { navigator.closeFrom(Routes.Settings) },
            navigation = SettingsNavigation(
                lessons = { page(SettingsCategory.LESSONS) },
                notifications = { page(SettingsCategory.NOTIFICATIONS) },
                calendar = { page(SettingsCategory.CALENDAR) },
                appearance = { page(SettingsCategory.APPEARANCE) },
                privacy = { page(SettingsCategory.PRIVACY) },
                diagnostics = { page(SettingsCategory.DIAGNOSTICS) },
                subjects = { go(Routes.Subjects) },
                backup = { go(Routes.Backup) },
                about = { go(Routes.About) },
            ),
            open = SettingsPages.entries.firstOrNull { it.value == open }?.key,
        )
    }
}

/** Where each row of the Settings index leads. */
private val SettingsPages: Map<SettingsCategory, Route> = mapOf(
    SettingsCategory.APPEARANCE to Routes.AppearanceSettings,
    SettingsCategory.NOTIFICATIONS to Routes.NotificationSettings,
    SettingsCategory.LESSONS to Routes.LessonSettings,
    SettingsCategory.CALENDAR to Routes.CalendarSettings,
    SettingsCategory.PRIVACY to Routes.PrivacySettings,
    SettingsCategory.DIAGNOSTICS to Routes.Diagnostics,
)

/** The detail opened from [list], while it is shown beside the list; null otherwise. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun openBeside(list: Route, stack: List<Screen>): Route? {
    if (LocalListDetailSceneScope.current == null) return null
    return stack.getOrNull(stack.indexOfLast { it.route == list } + 1)?.route
}

/** Whether this detail shares the window with the list it was opened from. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun besideList(): Boolean = LocalListDetailSceneScope.current != null

/** A student made by a setup detour, handed back to the form that asked for one. */
private data class CreatedStudent(val id: Long)

/** A group made by a setup detour, handed back to the booking form. */
private data class CreatedGroup(val id: Long)

/**
 * The list this route is the detail of, once the window has room for two panes: the student and group cards
 * beside People, each settings page beside the Settings index. Opened from anywhere else, a detail has no list
 * under it and fills the window. Null for a route that is not a detail.
 */
private fun Route.list(): Route? = when (this) {
    is Routes.StudentDetail, is Routes.StudentList, is Routes.GroupDetail, is Routes.GroupList -> Routes.People
    in SettingsPages.values -> Routes.Settings
    else -> null
}

// Keyed by the list, so a detail joins only its own list's scene.
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun Route.paneMetadata(): Map<String, Any> = when (this) {
    Routes.People -> ListDetailSceneStrategy.listPane(sceneKey = this) {
        PickDetail(R.string.people_pick_card, KlasIcons.Outlined.Group)
    }
    Routes.Settings -> ListDetailSceneStrategy.listPane(sceneKey = this) {
        PickDetail(R.string.settings_pick_page, KlasIcons.Outlined.Settings)
    }
    else -> list()?.let { ListDetailSceneStrategy.detailPane(sceneKey = it) }.orEmpty()
}

/**
 * The list under the detail on top of [backStack] — what a two-pane window shows beside it — or null when the
 * top is not a detail opened from its own list. A detail opened from another of the same list's details stacks
 * on it, beside the same list.
 */
fun listBesideDetail(backStack: List<Screen>): Route? {
    val list = backStack.last().route.list() ?: return null
    return backStack.dropLastWhile { it.route.list() == list }.lastOrNull()?.route?.takeIf { it == list }
}

@Composable
private fun PickDetail(@StringRes title: Int, icon: ImageVector) {
    EmptyState(title = stringResource(title), icon = icon)
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
