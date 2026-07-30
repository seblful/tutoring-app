package com.seblful.klas.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.CompositionLocalProvider
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.theme.KlasTheme
import com.seblful.klas.ui.backup.BackupScreen
import com.seblful.klas.ui.diagnostics.DiagnosticsScreen
import com.seblful.klas.ui.groups.GroupDetailScreen
import com.seblful.klas.ui.groups.GroupFormScreen
import com.seblful.klas.ui.more.AboutScreen
import com.seblful.klas.ui.more.MoreScreen
import com.seblful.klas.ui.more.SettingsScreen
import com.seblful.klas.ui.navigation.Routes
import com.seblful.klas.ui.navigation.TopDestination
import com.seblful.klas.ui.navigation.klasTabs
import com.seblful.klas.ui.navigation.screen
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

    // Both halves of "how does this look" come from the same row, and both default to the app's own
    // answer while it is still loading: no wallpaper colours for the frame before the setting arrives,
    // so a tutor who has them off never sees them flash on.
    KlasTheme(
        darkTheme = darkTheme,
        dynamicColor = settings?.dynamicColorEnabled == true,
    ) {
        CompositionLocalProvider(LocalCurrency provides (settings?.currency ?: Currency.BYN)) {
            when (rootViewModel.routeFor(settings)) {
                // Reached only if the splash has already been lifted — `MainActivity` holds it until
                // this row arrives, precisely so this state is not something the tutor sees. Kept as
                // the honest answer for the frame in which it is still true.
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

/**
 * The main tabs and the whole navigation graph.
 *
 * **The navigation chrome is a `NavigationSuiteScaffold`, and there is no `Scaffold` here at all.** That
 * is two fixes in one shape. The chrome now follows the window: a bottom bar on a phone, a rail down the
 * side once there is width for one, rather than a phone's bar across the foot of a ten-inch screen.
 *
 * And the double inset is gone. This used to be a `Scaffold` whose content padding already carried the
 * status-bar inset, wrapping a `NavHost` of screens that each compose *their own* `Scaffold` — whose
 * `TopAppBar` applies that same inset again, because a Compose inset is not consumed by a `padding()`
 * call. Every one of the four tab screens therefore sat a status bar too low while every pushed screen
 * sat correctly, which is the kind of difference that reads as "slightly off" without being locatable.
 * `NavigationSuiteScaffold` is built to wrap content that brings its own `Scaffold`, so the nesting that
 * caused it does not exist any more.
 */
@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = currentRoute in TopDestination.routes

    // **Which tab is marked is not "which route is showing".** Asked the second way, the answer while
    // anything is pushed is *none of them* — so the instant a form opened, every glyph in the bar fell
    // back to its outline and the tinted pill went out, and since the bar now stays for the length of
    // the change, that is a bar going visibly dead on its way off screen.
    //
    // A pushed screen sits on top of the tab it was opened from, so the back stack already holds the
    // answer: the deepest entry that is a tab is the one that owns whatever is above it. Read rather
    // than remembered, which is what keeps it right through a process death, a deep link and a tray tap
    // — a remembered "last tab seen" would be a guess restored from nothing on any arrival that did not
    // start at a tab.
    val backStack by navController.currentBackStack.collectAsState()
    val selectedTabRoute = backStack
        .lastOrNull { it.destination.route in TopDestination.routes }
        ?.destination?.route

    OpensTheLessonAReminderNames(navController)

    // **The chrome leaves with the screen, not ahead of it.** Whether the tabs apply is settled the
    // instant `navigate` is called, but the screen they belong to takes the length of a fade to go
    // (`ui/navigation/Transitions.kt`). Deciding this by swapping `layoutType` to `None` cut the bar
    // away on the transition's *first* frame, so the schedule — still fading out, still the only thing
    // on screen — was drawn reflowed into the space the bar had just left, and only then did the form
    // arrive. What the tutor watched disappear was part of the screen they were still looking at, which
    // is the whole of why it read as a glitch rather than as the chrome going away.
    //
    // `hide()` is a suspending transition, so the bar holds its place for the length of the change and
    // goes once the new screen has landed: the removal now happens under a screen that has arrived
    // rather than beneath one that is leaving. Measured on a 120Hz device it does **not** slide out —
    // it holds and then goes in one step — so this is a matter of *when* rather than of added motion.
    // Worth knowing before anyone tries to make it prettier.
    //
    // It also puts `layoutType` back to being *only* about the window's width — bar on a phone, rail
    // once there is room — which is the question it was always meant to answer.
    val chrome = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(onTab) { if (onTab) chrome.show() else chrome.hide() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            klasTabs(tabs = TopDestination.entries, selectedRoute = selectedTabRoute) { dest ->
                navController.navigate(dest.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        // Hidden on a pushed screen, which owns the whole window: a card or a form is not somewhere the
        // tabs apply. Still one call site, because a second would be a second composable identity and
        // would rebuild the `NavHost` — and with it the back stack.
        state = chrome,
        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.SCHEDULE,
            modifier = Modifier.fillMaxSize(),
        ) {
            screen(Routes.SCHEDULE) {
                ScheduleScreen(
                    onAddClass = { navController.navigate(Routes.classEdit(0)) },
                    onEditClass = { navController.navigate(Routes.classEdit(it)) },
                    // The way out of "nothing can be booked yet": the schedule's empty state offers
                    // the missing step rather than only naming it.
                    onAddStudent = { navController.navigate(Routes.STUDENT_ADD) },
                )
            }
            screen(Routes.REPORTS) {
                ReportsScreen(onStudentClick = { navController.navigate(Routes.studentDetail(it)) })
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

            screen(Routes.STUDENT_ADD) {
                StudentFormScreen(onBack = { navController.popBackStack() })
            }
            screen(
                Routes.STUDENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                StudentDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // Declared before the `group/{id}` card so the literal route can never be read as an id.
            screen(Routes.GROUP_ADD) {
                GroupFormScreen(onBack = { navController.popBackStack() })
            }
            screen(
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
            screen(
                Routes.GROUP_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                GroupFormScreen(onBack = { navController.popBackStack() })
            }

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

            screen(
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

/**
 * Sends the tutor where a notification they tapped asks them to go — a lesson for a reminder's
 * «Перенести», the reports for the weekly debt digest.
 *
 * A notification cannot show a date picker or list who owes what, so each carries an action and this is
 * where it becomes navigation. **Which** destination an intent asks for is [TrayNavigation.destinationFor]'s
 * and not this composable's: the app is opened by the launcher far more often than by a notification, and
 * getting that wrong would yank the tutor onto a lesson every time they open the app.
 *
 * **Both arrivals, because they are different arrivals.** A cold start has the intent waiting on the
 * activity; an app already running gets it through `onNewIntent`, which is the common case — the tutor
 * pulls the shade down over the app they were just in. Handling only the first would make the button work
 * exactly once per process.
 *
 * The `rememberSaveable` guard is about rotation: the activity is rebuilt and hands back the *same*
 * launch intent, which without it would re-open the lesson the tutor had already navigated away from.
 */
@Composable
private fun OpensTheLessonAReminderNames(navController: NavHostController) {
    val activity = LocalActivity.current as? ComponentActivity

    val open: (Intent) -> Unit = { intent ->
        val destination = TrayNavigation.destinationFor(
            action = intent.action,
            classId = intent.getLongExtra(TrayNavigation.EXTRA_CLASS_ID, 0L),
        )
        // `launchSingleTop` so two taps on the same notification do not stack the same screen twice.
        when (destination) {
            is TrayNavigation.Destination.Lesson ->
                navController.navigate(Routes.classEdit(destination.classId)) { launchSingleTop = true }
            // A **tab**, so it is switched to the way the bottom bar switches to it — popping back to the
            // start destination with the state saved. Pushing «Отчёты» on top of wherever the tutor was
            // would leave the reports sitting above the schedule with a back arrow and no tab selected.
            TrayNavigation.Destination.Debts -> navController.navigate(Routes.REPORTS) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            null -> Unit
        }
    }

    var launchIntentHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activity) {
        if (launchIntentHandled) return@LaunchedEffect
        launchIntentHandled = true
        activity?.intent?.let(open)
    }

    DisposableEffect(activity) {
        val listener = Consumer<Intent>(open)
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
}
