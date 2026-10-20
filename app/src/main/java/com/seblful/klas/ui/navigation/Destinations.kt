package com.seblful.klas.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.seblful.klas.R
import com.seblful.klas.ui.students.StudentTask
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Every destination in the app, as a type: navigating to one takes an instance, and its arguments are its
 * properties. Their names (`id`, `group`, `day`) are also the keys a view model reads them under from its
 * `SavedStateHandle`.
 */
object Routes {
    @Serializable
    data object Schedule

    /** Students and groups, as one tab with two views inside it. */
    @Serializable
    data object People

    /** Every student's and group's homework: what is current, and the archive of what is done. */
    @Serializable
    data object Homework

    @Serializable
    data object Reports

    @Serializable
    data object More

    @Serializable
    data object Subjects

    @Serializable
    data object Backup

    @Serializable
    data object Settings

    @Serializable
    data object LessonSettings

    @Serializable
    data object NotificationSettings

    @Serializable
    data object CalendarSettings

    @Serializable
    data object AppearanceSettings

    @Serializable
    data object Diagnostics

    @Serializable
    data object About

    @Serializable
    data object StudentAdd

    @Serializable
    data class StudentAddForSubject(val subject: Long = 0)

    @Serializable
    data class StudentDetail(val id: Long, val task: StudentTask? = null)

    @Serializable
    data object GroupAdd

    /**
     * A group's **card** — who is in it, what it has been taught, what its rate has been. Editing it
     * is [GroupEdit], reached from the card's pencil: tapping a group used to open the form directly,
     * which answered how to change a group and nothing about how one is going.
     */
    @Serializable
    data class GroupDetail(val id: Long, val setHomework: Boolean = false)

    /** The group form for an existing group — the only place its roster, subject or rate changes. */
    @Serializable
    data class GroupEdit(val id: Long)

    /**
     * id = 0 means "new class". [group] (non-zero) opens the form already booking for that group —
     * [student] preselects an individual. Profile entry uses the same conflict-checked form as Schedule.
     * [draft] is the schedule's quick-book sheet handing over what it had filled in (`BookingDraft`).
     */
    @Serializable
    data class ClassEdit(
        val id: Long,
        val group: Long = 0,
        val day: String? = null,
        val student: Long = 0,
        val draft: String? = null,
    )

    /**
     * [ClassEdit] with its day as a date. A string in the route because navigation carries a string with
     * no help, where a `LocalDate` would need its own `NavType` on every graph and test that declares it.
     */
    fun classEdit(id: Long, groupId: Long = 0, day: LocalDate? = null, studentId: Long = 0) =
        ClassEdit(id, groupId, day?.toString(), studentId)
}

/**
 * The top-level tabs of the bottom bar, **in the order they appear**: who is taught first («Люди» —
 * students and their groups, which are the same people seen as rosters), then what they were set
 * («Задания»), then when («Расписание»), then the money («Отчёты») and the rest. The app still *opens* on
 * the schedule; this order is only where each tab sits — and with five, it puts that tab in the middle.
 *
 * **Five again**, which is what folding «Группы» into «Люди» got the bar down from, and on purpose: that
 * tab was a second view of the same people, where homework is a question none of the other four answers —
 * "what have I set, and for whom", across every student. `TabLabel` already holds a label to one line and shrinks
 * it where «Расписание» would not fit a fifth of a 360dp screen, and `BottomBarLabelTest` is what proves
 * that on a device.
 *
 * **Every entry is a tab, always.** There used to be five, one of which («Группы») could be switched
 * off in Settings — which meant the bar, the route set and «Ещё» all had to be derived from a boolean
 * so that hiding the tab did not also hide the section or leave the bar with nothing selected. That
 * whole apparatus existed because two tabs were showing one idea; now they are one tab with a
 * segmented control inside it, and the list is simply [entries].
 */
enum class TopDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    /** Drawn while this is the current tab. */
    val icon: ImageVector,
    /**
     * Drawn while it is not — Material's pair, and the second signal the bar was missing. With one
     * filled glyph for both states the tinted pill was the only thing marking where you were.
     */
    val iconOutlined: ImageVector,
) {
    PEOPLE(Routes.People, R.string.nav_people, KlasIcons.Filled.Group, KlasIcons.Outlined.Group),
    HOMEWORK(
        Routes.Homework,
        R.string.nav_homework,
        KlasIcons.Filled.Assignment,
        KlasIcons.Outlined.Assignment,
    ),
    SCHEDULE(Routes.Schedule, R.string.nav_schedule, KlasIcons.Filled.CalendarMonth, KlasIcons.Outlined.CalendarMonth),
    REPORTS(Routes.Reports, R.string.nav_reports, KlasIcons.Filled.BarChart, KlasIcons.Outlined.BarChart),
    MORE(Routes.More, R.string.nav_more, KlasIcons.Filled.MoreHoriz, KlasIcons.Outlined.MoreHoriz),
    ;

    companion object {
        /** The tab [destination] is, or null for a screen pushed over one. */
        fun of(destination: NavDestination): TopDestination? =
            entries.firstOrNull { destination.hasRoute(it.route::class) }
    }
}

/**
 * The tab that owns whatever is on top of [backStack], or null when none is on it: **the deepest entry
 * that is a tab**. A screen pushed over a tab belongs to the tab it was opened from.
 */
fun owningTab(backStack: List<NavBackStackEntry>): TopDestination? =
    backStack.asReversed().firstNotNullOfOrNull { TopDestination.of(it.destination) }
