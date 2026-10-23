package com.seblful.klas.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.seblful.klas.R
import com.seblful.klas.ui.groups.GroupSection
import com.seblful.klas.ui.students.StudentSection
import com.seblful.klas.ui.students.StudentTask
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A destination in the app. Sealed, so the graph's `when` over it is checked for a screen per route and the
 * back stack serializes without reflection.
 */
@Serializable
sealed interface Route

/**
 * Every destination in the app, as a type: navigating to one takes an instance, and its arguments are its
 * properties. Their names (`id`, `group`, `day`) are also the keys a view model reads them under from its
 * `SavedStateHandle` (see `routeViewModel`).
 */
object Routes {
    /** The working day: what is on now, what is over and still to mark, what is owed. Where the app opens. */
    @Serializable
    data object Today : Route

    @Serializable
    data object Schedule : Route

    /** Students and groups, as one tab with two views inside it. */
    @Serializable
    data object People : Route

    /** Every student's and group's homework: what is current, and the archive of what is done. */
    @Serializable
    data object Homework : Route

    @Serializable
    data object Reports : Route

    @Serializable
    data object Subjects : Route

    @Serializable
    data object Backup : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object LessonSettings : Route

    @Serializable
    data object NotificationSettings : Route

    @Serializable
    data object CalendarSettings : Route

    @Serializable
    data object AppearanceSettings : Route

    @Serializable
    data object PrivacySettings : Route

    @Serializable
    data object Diagnostics : Route

    @Serializable
    data object About : Route

    @Serializable
    data object StudentAdd : Route

    @Serializable
    data class StudentAddForSubject(val subject: Long = 0) : Route

    @Serializable
    data class StudentDetail(val id: Long, val task: StudentTask? = null) : Route

    /** A student's name, contacts, note and place, corrected on a screen of their own. */
    @Serializable
    data class StudentEdit(val id: Long) : Route

    /** One of a student's lists in full — what «See all» under a section of their card opens. */
    @Serializable
    data class StudentList(val id: Long, val section: StudentSection) : Route

    @Serializable
    data object GroupAdd : Route

    /**
     * A group's **card** — who is in it, what it has been taught, what its rate has been. Editing it
     * is [GroupEdit], reached from the card's pencil: tapping a group used to open the form directly,
     * which answered how to change a group and nothing about how one is going.
     */
    @Serializable
    data class GroupDetail(val id: Long, val setHomework: Boolean = false) : Route

    /** One of a group's lists in full — what «See all» under a section of its card opens. */
    @Serializable
    data class GroupList(val id: Long, val section: GroupSection) : Route

    /** The group form for an existing group — the only place its roster, subject or rate changes. */
    @Serializable
    data class GroupEdit(val id: Long) : Route

    /**
     * id = 0 means "new class". [group] (non-zero) opens the form already booking for that group —
     * [student] preselects an individual. Profile entry uses the same conflict-checked form as Schedule.
     */
    @Serializable
    data class ClassEdit(
        val id: Long,
        val group: Long = 0,
        val day: String? = null,
        val student: Long = 0,
        /** A lesson to book again a week later — the sheet's «Book the next one like this». */
        val like: Long = 0,
    ) : Route

    /**
     * [ClassEdit] with its day as a date. A string in the route because navigation carries a string with
     * no help, where a `LocalDate` would need its own `NavType` on every graph and test that declares it.
     */
    fun classEdit(id: Long, groupId: Long = 0, day: LocalDate? = null, studentId: Long = 0, like: Long = 0) =
        ClassEdit(id, groupId, day?.toString(), studentId, like)
}

/**
 * The top-level tabs of the bottom bar, **in the order they appear**: the working day first («Сегодня», where
 * the app opens), then the week around it («Расписание»), then who is taught («Люди»), what they were set
 * («Задания») and the money («Отчёты»). Settings — with the subjects, the backup and the app's own pages that
 * «Ещё» used to hold — is the gear on every tab's bar, not a tab: it is visited, not worked in.
 *
 * `TabLabel` holds a label to one line and shrinks it where «Расписание» would not fit a fifth of a 360dp
 * screen, and `BottomBarLabelTest` is what proves that on a device.
 *
 * **Every entry is a tab, always.** There used to be five, one of which («Группы») could be switched
 * off in Settings — which meant the bar, the route set and «Ещё» all had to be derived from a boolean
 * so that hiding the tab did not also hide the section or leave the bar with nothing selected. That
 * whole apparatus existed because two tabs were showing one idea; now they are one tab with a
 * segmented control inside it, and the list is simply [entries].
 */
@Serializable
enum class TopDestination(
    val route: Route,
    @param:StringRes val labelRes: Int,
    /** Drawn while this is the current tab. */
    val icon: ImageVector,
    /**
     * Drawn while it is not — Material's pair, and the second signal the bar was missing. With one
     * filled glyph for both states the tinted pill was the only thing marking where you were.
     */
    val iconOutlined: ImageVector,
) {
    TODAY(Routes.Today, R.string.nav_today, KlasIcons.Filled.Today, KlasIcons.Outlined.Today),
    SCHEDULE(Routes.Schedule, R.string.nav_schedule, KlasIcons.Filled.CalendarMonth, KlasIcons.Outlined.CalendarMonth),
    PEOPLE(Routes.People, R.string.nav_people, KlasIcons.Filled.Group, KlasIcons.Outlined.Group),
    HOMEWORK(
        Routes.Homework,
        R.string.nav_homework,
        KlasIcons.Filled.Assignment,
        KlasIcons.Outlined.Assignment,
    ),
    REPORTS(Routes.Reports, R.string.nav_reports, KlasIcons.Filled.BarChart, KlasIcons.Outlined.BarChart),
    ;

    companion object {
        /** The tab the app opens on, always at the bottom of the back stack. */
        val START = TODAY

        /** The tab [route] is, or null for a screen pushed over one. */
        fun of(route: Route): TopDestination? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The tab that owns whatever is on top of [backStack], or null when none is on it: **the deepest entry
 * that is a tab**. A screen pushed over a tab belongs to the tab it was opened from.
 */
fun owningTab(backStack: List<Screen>): TopDestination? =
    backStack.asReversed().firstNotNullOfOrNull { TopDestination.of(it.route) }
