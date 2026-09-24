package com.seblful.klas.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.seblful.klas.R

/** Route constants for the whole app. */
object Routes {
    const val SCHEDULE = "schedule"

    /** Students and groups, as one tab with two views inside it. */
    const val PEOPLE = "people"

    /** Every student's and group's homework: what is current, and the archive of what is done. */
    const val HOMEWORK = "homework"
    const val REPORTS = "reports"
    const val MORE = "more"

    const val SUBJECTS = "subjects"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val STUDENT_ADD = "student/new"

    const val STUDENT_DETAIL = "student/{id}"
    fun studentDetail(id: Long) = "student/$id"

    const val GROUP_ADD = "group/new"

    /**
     * A group's **card** — who is in it, what it has been taught, what its rate has been. Editing it
     * is [GROUP_EDIT], reached from the card's pencil: tapping a group used to open the form directly,
     * which answered how to change a group and nothing about how one is going.
     */
    const val GROUP_DETAIL = "group/{id}"
    fun groupDetail(id: Long) = "group/$id"

    /** The group form for an existing group — the only place its roster, subject or rate changes. */
    const val GROUP_EDIT = "group/{id}/edit"
    fun groupEdit(id: Long) = "group/$id/edit"

    const val CLASS_EDIT = "class/{id}?group={group}"

    /**
     * id = 0 means "new class". [groupId] (non-zero) opens the form already booking for that group —
     * an entry point the form still honours, though **no screen passes it today**: booking lives on
     * the schedule, which is the one place that knows what else the hour holds.
     *
     * There is deliberately no date argument: the booking form starts with an empty date on purpose
     * (see [com.seblful.klas.ui.schedule.ClassForm]), so the one that used to be declared here
     * was passed by the schedule and read by nobody.
     */
    fun classEdit(id: Long, groupId: Long = 0) = "class/$id?group=$groupId"
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
    val route: String,
    @param:StringRes val labelRes: Int,
    /** Drawn while this is the current tab. */
    val icon: ImageVector,
    /**
     * Drawn while it is not — Material's pair, and the second signal the bar was missing. With one
     * filled glyph for both states the tinted pill was the only thing marking where you were.
     */
    val iconOutlined: ImageVector,
) {
    PEOPLE(Routes.PEOPLE, R.string.nav_people, Icons.Filled.People, Icons.Outlined.People),
    HOMEWORK(
        Routes.HOMEWORK,
        R.string.nav_homework,
        Icons.AutoMirrored.Filled.Assignment,
        Icons.AutoMirrored.Outlined.Assignment,
    ),
    SCHEDULE(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    REPORTS(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz);

    companion object {
        /** The routes that are tabs — what the bottom bar is shown for. */
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}
