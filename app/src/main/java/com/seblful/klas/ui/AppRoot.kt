package com.seblful.klas.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.lock.AppLockGate
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.LocalRailAction
import com.seblful.klas.ui.common.LocalTabBarClearance
import com.seblful.klas.ui.common.LocalTabReselect
import com.seblful.klas.ui.common.ProvideClockFormat
import com.seblful.klas.ui.common.ProvideLocalizedResources
import com.seblful.klas.ui.common.RailAction
import com.seblful.klas.ui.common.TabReselect
import com.seblful.klas.ui.navigation.DockedTabBar
import com.seblful.klas.ui.navigation.KlasGraph
import com.seblful.klas.ui.navigation.KlasNavigator
import com.seblful.klas.ui.navigation.KlasTabs
import com.seblful.klas.ui.navigation.OpensWhatTheTrayNames
import com.seblful.klas.ui.navigation.Routes
import com.seblful.klas.ui.navigation.Screen
import com.seblful.klas.ui.navigation.TopDestination
import com.seblful.klas.ui.navigation.chromeEnter
import com.seblful.klas.ui.navigation.chromeExit
import com.seblful.klas.ui.navigation.fadeThrough
import com.seblful.klas.ui.navigation.listBesideDetail
import com.seblful.klas.ui.navigation.owningTab
import com.seblful.klas.ui.navigation.predictiveBackPop
import com.seblful.klas.ui.navigation.rememberKlasNavigator
import com.seblful.klas.ui.onboarding.FirstLesson
import com.seblful.klas.ui.onboarding.OnboardingScreen
import com.seblful.klas.ui.theme.KlasTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch

@Composable
fun AppRoot(rootViewModel: RootViewModel = metroViewModel(), onUnlock: (title: String) -> Unit = {}) {
    // The upkeep pass that the tutor's own arrival is worth: settle, in process, whatever the clock
    // passed while the app was closed, so the schedule and the reports they are about to look at are
    // current — and so the passes that run while nobody is here keep the report they are owed. See
    // [RootViewModel.catchUp] for why this is not `Application.onCreate`'s to do.
    LaunchedEffect(Unit) { rootViewModel.catchUp() }

    val settings = rootViewModel.settings.collectAsStateWithLifecycle().value

    // A row that has not loaded follows the system, like a tutor who chose to; only resolving «follow
    // the system» is this composable's, because that answer comes from the platform.
    val darkTheme = when (settings?.themeMode ?: ThemeChoice.SYSTEM) {
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
        // **The whole composition's language, provided here** — what that provision consists of, and why it
        // cannot be `MainActivity`'s, is [ProvideLocalizedResources]'. Resolving «follow the system» is
        // `localeFor`'s, because that answer comes from the platform; a composable may not be the second
        // place that knows what either of those means.
        ProvideLocalizedResources(rootViewModel.localeFor(settings)) {
            // The currency while the row is loading is the app's oldest, not the phone's: nothing is drawn
            // in this state (the splash is still up), and `SettingsRepository` is the one place that knows
            // what an unwritten row means on this device.
            CompositionLocalProvider(LocalCurrency provides (settings?.currency ?: Currency.BYN)) {
                // Whether the phone counts hours to 12 or 24, asked once for every time printed below.
                ProvideClockFormat {
                    RootSwitch(rootViewModel.routeFor(settings)) { route ->
                        when (route) {
                            // Reached only if the splash has already been lifted — `MainActivity` holds it
                            // until this row arrives, precisely so this state is not something the tutor
                            // sees. Kept as the honest answer for the frame in which it is still true.
                            RootRoute.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            RootRoute.ONBOARDING ->
                                OnboardingScreen(onFinished = { /* settings flip re-renders into MainApp */ })
                            RootRoute.MAIN -> {
                                val open by rootViewModel.appLock.open.collectAsStateWithLifecycle()
                                val available by rootViewModel.appLock.available.collectAsStateWithLifecycle()
                                // A phone with no screen lock left is never locked out of its own data.
                                AppLockGate(settings?.privacy?.appLock == true && available && !open, onUnlock) {
                                    MainApp(rootViewModel.firstLesson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Whichever of [RootRoute] is current. Out of loading it cuts: the splash covers that change and makes its own
 * way out (`SplashHandover.kt`). Setup gives way to the app — finished, skipped or restored — by fade through,
 * as every screen arrives.
 */
@Composable
internal fun RootSwitch(route: RootRoute, content: @Composable (RootRoute) -> Unit) {
    AnimatedContent(
        targetState = route,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (initialState == RootRoute.LOADING) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeThrough()
            }
        },
        label = "root",
    ) { content(it) }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainApp(firstLesson: FirstLesson) {
    val navigator = rememberKlasNavigator()
    OpensFirstLesson(navigator, firstLesson)
    val backStack = navigator.backStack

    // The same pane directive decides both whether a detail sits beside its list and, below, whether the
    // tabs stay up for it: they do beside a tab (People), not beside a pushed list (Settings).
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val listDetail = rememberListDetailSceneStrategy<Screen>(directive = directive)
    val besideTab = directive.maxHorizontalPartitions > 1 &&
        listBesideDetail(backStack)?.let(TopDestination::of) != null
    val onTab = TopDestination.of(navigator.top.route) != null || besideTab

    // **Which tab is marked is not "which route is showing".** Asked the second way, the answer while
    // anything is pushed is *none of them* — so the instant a form opened, every glyph in the bar fell
    // back to its outline and the tinted pill went out, and since the bar now stays for the length of
    // the change, that is a bar going visibly dead on its way off screen.
    //
    // A pushed screen sits on top of the tab it was opened from, so the back stack already holds the
    // answer: the deepest entry that is a tab is the one that owns whatever is above it. Read rather
    // than remembered, which is what keeps it right through a process death and a tray tap — a
    // remembered "last tab seen" would be a guess restored from nothing on any arrival that did not
    // start at a tab.
    val selectedTab = owningTab(backStack)

    OpensWhatTheTrayNames(navigator)
    val graph = remember(navigator) { KlasGraph(navigator) }
    // Material's current components for the width: the short bar on a phone (a landscape one included), the
    // wide rail from medium width on. Where Material would draw its bar, the app docks its own over the screens
    // instead (`DockedTabBar`), and the suite draws nothing.
    val suiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val railLayout by rememberUpdatedState(suiteType in RailTypes)
    val phoneBar = suiteType in BarTypes
    var barClearance by remember { mutableStateOf(EstimatedBarClearance) }
    val tabClearance by rememberUpdatedState(if (phoneBar) barClearance else 0.dp)
    // Beside a tab the rail carries the tab's add button in its header; a phone's bar leaves it to the
    // screen, which keeps clear of the bar instead. Whether a screen is a tab's is the entry's own fact, asked
    // once: a tab fading out under a pushed form keeps its button in the rail, and a pushed screen fading out
    // over a tab never offers one.
    val railAction = remember { RailAction() }
    val tabChrome = rememberTabChrome(navigator, railAction, { railLayout }, { tabClearance })

    // Decorated over every live screen, not only the shown ones: a tab's screens set aside while another
    // tab shows keep their view models and saved state, as `NavHost`'s saved tabs did.
    val entries = rememberEntries(navigator, graph, tabChrome)

    // **The chrome leaves with the screen, not ahead of it.** Whether the tabs apply is settled the
    // instant `navigate` is called, but the screen they belong to takes the length of a fade to go
    // (`ui/navigation/Transitions.kt`). Swapping `layoutType` to `None` would cut the bar away on the
    // transition's *first* frame, so the schedule — still fading out, still the only thing on screen —
    // would be drawn reflowed into the space the bar had just left, and only then would the form
    // arrive. What the tutor watched disappear would be part of the screen they were still looking at,
    // which reads as a glitch rather than as the chrome going away.
    //
    // `hide()` is a suspending transition, so the bar holds its place for the length of the change and
    // goes once the new screen has landed: the removal happens under a screen that has arrived rather
    // than beneath one that is leaving. Measured on a 120Hz device it does **not** slide out — it holds
    // and then goes in one step — so this is a matter of *when* rather than of added motion. Worth
    // knowing before anyone tries to make it prettier.
    //
    // It also leaves the suite's type to be *only* about the window's width — bar on a phone, rail once
    // there is room.
    //
    // The phone's docked bar is past all this: laid over the screens, its going reflows nothing, so it
    // simply fades on the screen change's own beat (`chromeEnter`/`chromeExit`). The state below is the rail's.
    val shiftInset = with(LocalDensity.current) { PredictiveShiftInset.roundToPx() }
    // Starting as it should be: a pushed screen restored after the process died does not flash the tabs.
    val chrome = rememberNavigationSuiteScaffoldState(
        if (onTab) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden,
    )
    LaunchedEffect(onTab) { if (onTab) chrome.show() else chrome.hide() }

    val reselect = remember { TabReselect() }
    val onTabTap = rememberTabTap(navigator, reselect)
    NavigationSuiteScaffold(
        // A camera cutout at the side is the whole app's to clear, once: the rail would sit under it, and the
        // screens beside the rail would pad for it again.
        modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        navigationItems = {
            KlasTabs(tabs = TopDestination.entries, selected = selectedTab, onSelect = onTabTap)
        },
        // Hidden on a pushed screen, which owns the whole window: a card or a form is not somewhere the
        // tabs apply — unless the card shares the window with the People list. Still one call site,
        // because a second would be a second composable identity and would rebuild the `NavDisplay`.
        state = chrome,
        navigationSuiteType = if (phoneBar) NavigationSuiteType.None else suiteType,
        // Always a button's height, so the tabs below stay put between a tab with one and a tab without.
        primaryActionContent = { if (railLayout) RailHeader(railAction) },
    ) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                NavDisplay(
                    entries = entries,
                    modifier = Modifier.fillMaxSize(),
                    sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
                    transitionSpec = { fadeThrough() },
                    popTransitionSpec = { fadeThrough() },
                    predictivePopTransitionSpec = { edge -> predictiveBackPop(edge, shiftInset) },
                    onBack = navigator::goBack,
                )
            }
            // After the `NavDisplay`, so it is drawn over the screens; it never moves them.
            if (phoneBar) PhoneTabs(onTab, selectedTab, onTabTap) { barClearance = it }
        }
    }
}

/** A phone's tabs at the foot of the window while a tab is [visible], fading with the screen they belong to. */
@Composable
private fun BoxScope.PhoneTabs(
    visible: Boolean,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    onClearance: (Dp) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = chromeEnter(),
        exit = chromeExit(),
    ) {
        DockedTabBar(TopDestination.entries, selected, onSelect, onClearance)
    }
}

/** A tab tap: another tab opens, the one already on top goes back to the top of its page — see [TabReselect]. */
@Composable
private fun rememberTabTap(navigator: KlasNavigator, reselect: TabReselect): (TopDestination) -> Unit {
    val scope = rememberCoroutineScope()
    return { tab ->
        if (navigator.isOnTab(tab)) scope.launch { reselect.reselected() } else navigator.navigateToTab(tab)
    }
}

/** Setup's «Book the first lesson»: the booking form for its student, over Today, once. */
@Composable
private fun OpensFirstLesson(navigator: KlasNavigator, firstLesson: FirstLesson) {
    LaunchedEffect(navigator) {
        firstLesson.take()?.let { student -> navigator.navigate(Routes.classEdit(0, studentId = student)) }
    }
}

/** The shown screens, each decorated over every live one — see `MainApp`. */
@Composable
private fun rememberEntries(
    navigator: KlasNavigator,
    graph: KlasGraph,
    tabChrome: NavEntryDecorator<Screen>,
): List<NavEntry<Screen>> {
    val decorated = rememberDecoratedNavEntries(
        backStack = navigator.live,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberResultEventBusNavEntryDecorator(),
            tabChrome,
        ),
        entryProvider = graph::entry,
    ).associateBy { it.contentKey }
    return navigator.backStack.map { decorated.getValue(it.id) }
}

/**
 * Gives a tab's screen what the tabs lend it: the rail to hand its add button to, while [railLayout], and the
 * room the phone's tab bar takes at its foot, [barClearance]. Whether it is a tab's is asked once per entry, so a
 * screen fading out keeps the answer it arrived with.
 */
@Composable
private fun rememberTabChrome(
    navigator: KlasNavigator,
    railAction: RailAction,
    railLayout: () -> Boolean,
    barClearance: () -> Dp,
): NavEntryDecorator<Screen> = remember(navigator, railAction) {
    NavEntryDecorator(
        decorate = { entry ->
            val tab = remember(entry.contentKey) {
                val screen = navigator.live.firstOrNull { it.id == entry.contentKey }
                screen?.let { TopDestination.of(it.route) } != null
            }
            CompositionLocalProvider(
                LocalRailAction provides railAction.takeIf { tab && railLayout() },
                LocalTabBarClearance provides if (tab) barClearance() else 0.dp,
            ) {
                entry.Content()
            }
        },
    )
}

/** The rail's header: the tab's add button, in a slot a button's height whether or not there is one. */
@Composable
private fun RailHeader(railAction: RailAction) {
    Box(Modifier.defaultMinSize(minHeight = RailActionHeight), contentAlignment = Alignment.Center) {
        railAction.content?.invoke()
    }
}

/** The suite's types that are rails, whose header carries a tab's add button. */
private val RailTypes = setOf(
    NavigationSuiteType.WideNavigationRailCollapsed,
    NavigationSuiteType.WideNavigationRailExpanded,
)

/** The suite's types that are bars, which a phone draws as a `DockedTabBar` instead. */
private val BarTypes = setOf(
    NavigationSuiteType.ShortNavigationBarCompact,
    NavigationSuiteType.ShortNavigationBarMedium,
)

/** The phone's bar's room until it is first measured: its hairline and row at the default font size. */
private val EstimatedBarClearance = 65.dp

/** A FAB's height, which the rail's header keeps whether or not the tab has one. */
private val RailActionHeight = 56.dp

private val PredictiveShiftInset = 8.dp
