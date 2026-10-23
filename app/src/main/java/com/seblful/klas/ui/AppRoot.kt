package com.seblful.klas.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import com.seblful.klas.ui.common.LocalTabReselect
import com.seblful.klas.ui.common.ProvideClockFormat
import com.seblful.klas.ui.common.ProvideLocalizedResources
import com.seblful.klas.ui.common.RailAction
import com.seblful.klas.ui.common.TabReselect
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.KlasGraph
import com.seblful.klas.ui.navigation.KlasNavigator
import com.seblful.klas.ui.navigation.KlasTabs
import com.seblful.klas.ui.navigation.OpensWhatTheTrayNames
import com.seblful.klas.ui.navigation.Screen
import com.seblful.klas.ui.navigation.TopDestination
import com.seblful.klas.ui.navigation.fadeThrough
import com.seblful.klas.ui.navigation.listBesideDetail
import com.seblful.klas.ui.navigation.owningTab
import com.seblful.klas.ui.navigation.predictiveBackPop
import com.seblful.klas.ui.navigation.rememberKlasNavigator
import com.seblful.klas.ui.onboarding.OnboardingScreen
import com.seblful.klas.ui.schedule.ScheduleTarget
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

    // Read into a plain local rather than a `by` delegate: a delegated property is a getter call each
    // time it is read, so Kotlin cannot smart-cast it and the branches below had to re-assert with
    // `settings!!` what `settings == null` had just ruled out.
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
                                    MainApp()
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
private fun MainApp() {
    val navigator = rememberKlasNavigator()
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

    /**
     * The latest tray tap that asked a tab for something more than itself — the schedule for a day or a
     * lesson's sheet, the reports for their debts. See [Arrival], and why each tap is its own instance.
     * Bound to the screen it landed on, so a tab preserved below a draft cannot replay the tap later.
     */
    var scheduleArrival by remember { mutableStateOf<Pair<Long, Arrival<ScheduleTarget>>?>(null) }
    var debtsArrival by remember { mutableStateOf<Pair<Long, Arrival<Unit>>?>(null) }

    val arriveAtSchedule: (ScheduleTarget) -> Unit = { target -> scheduleArrival = navigator.top.id to Arrival(target) }
    val arriveAtDebts = { debtsArrival = navigator.top.id to Arrival(Unit) }
    OpensWhatTheTrayNames(navigator, onSchedule = arriveAtSchedule, onDebts = arriveAtDebts)

    val graph = remember(navigator) {
        KlasGraph(
            navigator,
            scheduleArrival = { id -> scheduleArrival?.takeIf { it.first == id }?.second },
            debtsArrival = { id -> debtsArrival?.takeIf { it.first == id }?.second },
            arriveAtSchedule = arriveAtSchedule,
            arriveAtDebts = arriveAtDebts,
        )
    }
    // Material's current components for the width: the short bar on a phone (a landscape one included), the
    // wide rail from medium width on.
    val suiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val railLayout by rememberUpdatedState(suiteType in RailTypes)
    // Beside a tab the rail carries the tab's add button in its header; a bar leaves it to the screen. Whether
    // a screen is a tab's is the entry's own fact, asked once: a tab fading out under a pushed form keeps its
    // button in the rail, and a pushed screen fading out over a tab never offers one.
    val railAction = remember { RailAction() }
    val railOffer = rememberRailOffer(navigator, railAction) { railLayout }

    // Decorated over every live screen, not only the shown ones: a tab's screens set aside while another
    // tab shows keep their view models and saved state, as `NavHost`'s saved tabs did.
    val entries = rememberEntries(navigator, graph, railOffer)

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
    // It also leaves the suite's type to be *only* about the window's width — bar on a phone, rail once
    // there is room — which is the question it was always meant to answer.
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
        navigationSuiteType = suiteType,
        // Always a button's height, so the tabs below stay put between a tab with one and a tab without.
        primaryActionContent = { if (railLayout) RailHeader(railAction) },
    ) {
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

/** The shown screens, each decorated over every live one — see `MainApp`. */
@Composable
private fun rememberEntries(
    navigator: KlasNavigator,
    graph: KlasGraph,
    railOffer: NavEntryDecorator<Screen>,
): List<NavEntry<Screen>> {
    val decorated = rememberDecoratedNavEntries(
        backStack = navigator.live,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberResultEventBusNavEntryDecorator(),
            railOffer,
        ),
        entryProvider = graph::entry,
    ).associateBy { it.contentKey }
    return navigator.backStack.map { decorated.getValue(it.id) }
}

/**
 * Gives a tab's screen the rail to hand its add button to, while [railLayout] — asked once per entry, so a
 * screen fading out keeps the answer it arrived with.
 */
@Composable
private fun rememberRailOffer(
    navigator: KlasNavigator,
    railAction: RailAction,
    railLayout: () -> Boolean,
): NavEntryDecorator<Screen> = remember(navigator, railAction) {
    NavEntryDecorator(
        decorate = { entry ->
            val tab = remember(entry.contentKey) {
                val screen = navigator.live.firstOrNull { it.id == entry.contentKey }
                screen?.let { TopDestination.of(it.route) } != null
            }
            CompositionLocalProvider(LocalRailAction provides railAction.takeIf { tab && railLayout() }) {
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

/** A FAB's height, which the rail's header keeps whether or not the tab has one. */
private val RailActionHeight = 56.dp

private val PredictiveShiftInset = 8.dp
