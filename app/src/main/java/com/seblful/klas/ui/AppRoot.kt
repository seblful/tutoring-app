package com.seblful.klas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.ProvideLocalizedResources
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.KlasGraph
import com.seblful.klas.ui.navigation.OpensWhatTheTrayNames
import com.seblful.klas.ui.navigation.Screen
import com.seblful.klas.ui.navigation.TopDestination
import com.seblful.klas.ui.navigation.cardBesideList
import com.seblful.klas.ui.navigation.crossFade
import com.seblful.klas.ui.navigation.klasTabs
import com.seblful.klas.ui.navigation.owningTab
import com.seblful.klas.ui.navigation.rememberKlasNavigator
import com.seblful.klas.ui.onboarding.OnboardingScreen
import com.seblful.klas.ui.schedule.ScheduleTarget
import com.seblful.klas.ui.theme.KlasTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppRoot(rootViewModel: RootViewModel = metroViewModel()) {
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
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainApp() {
    val navigator = rememberKlasNavigator()
    val backStack = navigator.backStack

    // The same pane directive decides both whether a card sits beside the People list and, below, whether
    // the tabs stay up for it.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val listDetail = rememberListDetailSceneStrategy<Screen>(directive = directive)
    val besideList = directive.maxHorizontalPartitions > 1 && cardBesideList(backStack)
    val onTab = TopDestination.of(navigator.top.route) != null || besideList

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

    OpensWhatTheTrayNames(
        navigator,
        onSchedule = { target -> scheduleArrival = navigator.top.id to Arrival(target) },
        onDebts = { debtsArrival = navigator.top.id to Arrival(Unit) },
    )

    val graph = remember(navigator) {
        KlasGraph(
            navigator,
            scheduleArrival = { id -> scheduleArrival?.takeIf { it.first == id }?.second },
            debtsArrival = { id -> debtsArrival?.takeIf { it.first == id }?.second },
        )
    }
    // Decorated over every live screen, not only the shown ones: a tab's screens set aside while another
    // tab shows keep their view models and saved state, as `NavHost`'s saved tabs did.
    val decorated = rememberDecoratedNavEntries(
        backStack = navigator.live,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberResultEventBusNavEntryDecorator(),
        ),
        entryProvider = graph::entry,
    ).associateBy { it.contentKey }
    val entries = backStack.map { decorated.getValue(it.id) }

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
            klasTabs(tabs = TopDestination.entries, selected = selectedTab, onSelect = navigator::navigateToTab)
        },
        // Hidden on a pushed screen, which owns the whole window: a card or a form is not somewhere the
        // tabs apply — unless the card shares the window with the People list. Still one call site,
        // because a second would be a second composable identity and would rebuild the `NavDisplay`.
        state = chrome,
        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    ) {
        NavDisplay(
            entries = entries,
            modifier = Modifier.fillMaxSize(),
            sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
            transitionSpec = { crossFade() },
            popTransitionSpec = { crossFade() },
            predictivePopTransitionSpec = { crossFade() },
            onBack = navigator::goBack,
        )
    }
}
