package com.seblful.klas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import androidx.savedstate.compose.serialization.serializers.SnapshotStateMapSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.random.Random

/**
 * One place on the back stack: a [route], and the [id] that tells two visits to it apart — a student's
 * card opened again from one of their groups, the schedule a tray tap put over a form opened from it. The
 * id is the entry's content key, so each visit keeps its own view model and saved state.
 */
@Serializable
data class Screen(val route: Route, val id: Long = Random.nextLong())

/**
 * The app's back stack: **one list**, the start tab at its bottom, as `NavHost` kept it. A tray tap can put a
 * tab over a form opened from another (Back returns to the draft), which per-tab stacks cannot hold.
 *
 * Switching tabs sets aside the screens above the start tab under the tab that owned them and brings back the
 * chosen tab's, so each tab keeps where the tutor was in it. Set-aside screens keep their state: see [live].
 */
class KlasNavigator internal constructor(
    private val stack: SnapshotStateList<Screen> = mutableStateListOf(Screen(TopDestination.START.route)),
    private val setAside: SnapshotStateMap<TopDestination, List<Screen>> = mutableStateMapOf(),
) {
    /** What is showing, bottom first: always the start tab, then whatever is above it. */
    val backStack: List<Screen> get() = stack

    val top: Screen get() = stack.last()

    /** Every screen that is shown or waiting with its tab — the ones whose state must not be dropped. */
    val live: List<Screen> get() = stack + setAside.values.flatten()

    val arrivals = TabArrivals(this)

    fun navigate(route: Route) {
        stack += Screen(route)
    }

    /** Pops the top screen. The start tab alone is left to the system, which closes the app. */
    fun goBack() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /**
     * Pops [screen] if it is still on top — a screen's own back arrow. A leaving screen stays tappable for the
     * length of its fade, so a second tap on the arrow would otherwise close the screen under it too.
     */
    fun close(screen: Screen) {
        if (top.id == screen.id) goBack()
    }

    /**
     * Pushes [route] in place of what is above [under], the nearest screen with that route — a list opening
     * a detail beside it replaces the one it showed instead of stacking them. With [under] not on the stack
     * it is an ordinary [navigate].
     */
    fun replaceAbove(under: Route, route: Route) {
        val at = stack.indexOfLast { it.route == under }
        if (at >= 0) stack.removeRange(at + 1, stack.size)
        navigate(route)
    }

    /**
     * Closes the nearest screen with [route] and everything above it — a list's back arrow, which leaves with
     * the detail beside it. The start tab stays.
     */
    fun closeFrom(route: Route) {
        val at = stack.indexOfLast { it.route == route }
        if (at > 0) stack.removeRange(at, stack.size)
    }

    /** Whether [tab]'s own screen is the one on top — a tap on it then sends its page to the top instead. */
    fun isOnTab(tab: TopDestination): Boolean = TopDestination.of(top.route) == tab

    /**
     * Switches to [tab] the way the bar does, keeping each tab's screens. External interruptions use
     * [openTabFromTray] to preserve a pushed form.
     *
     * **The tab already showing is left alone.** Switching to it would re-target the fade it is arriving
     * with: a second tap inside the first frames of that fade made the screen lurch forward instead.
     *
     * **A screen pushed over the tab asked for is closed.** A screen pushed over a different tab waits with
     * its tab.
     */
    fun navigateToTab(tab: TopDestination) {
        if (TopDestination.of(top.route) == tab) return
        val owner = owningTab(stack) ?: TopDestination.START
        if (owner == tab) {
            stack.removeRange(stack.indexOfLast { it.route == tab.route } + 1, stack.size)
            return
        }
        setAside[owner] = stack.drop(1)
        val restored = setAside.remove(tab)
            ?: if (tab == TopDestination.START) emptyList() else listOf(Screen(tab.route))
        stack.removeRange(1, stack.size)
        stack.addAll(restored)
    }

    /** External interruptions keep the current screen and its draft on the stack for Back. */
    fun openTabFromTray(tab: TopDestination) {
        if (TopDestination.of(top.route) == null) navigate(tab.route) else navigateToTab(tab)
    }

    /**
     * Opens lesson [classId]'s form for a tray tap, **over whatever is open** — and not twice: a second tap on
     * the same notification finds the form already on top and leaves it.
     */
    fun openLessonForm(classId: Long) {
        if ((top.route as? Routes.ClassEdit)?.id != classId) navigate(Routes.classEdit(classId))
    }

    /**
     * Pushes [route] for a tap from outside the app, **over whatever is open**, unless it is already on top —
     * a second tap on the same widget button leaves the form it opened, and its draft, alone.
     */
    fun openOnTop(route: Route) {
        if (top.route != route) navigate(route)
    }
}

/** The app's [KlasNavigator], kept across configuration changes and process death. */
@Composable
fun rememberKlasNavigator(): KlasNavigator {
    val stack = rememberSerializable(serializer = SnapshotStateListSerializer(Screen.serializer())) {
        mutableStateListOf(Screen(TopDestination.START.route))
    }
    val setAside = rememberSerializable(
        serializer = SnapshotStateMapSerializer(TopDestination.serializer(), ListSerializer(Screen.serializer())),
    ) { mutableStateMapOf() }
    return remember(stack, setAside) { KlasNavigator(stack, setAside) }
}
