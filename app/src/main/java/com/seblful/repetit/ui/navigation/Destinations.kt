package com.seblful.repetit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.seblful.repetit.R

/** Route constants for the whole app. */
object Routes {
    const val SCHEDULE = "schedule"
    const val STUDENTS = "students"
    const val REPORTS = "reports"
    const val MORE = "more"

    const val SUBJECTS = "subjects"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val STUDENT_ADD = "student/new"

    const val STUDENT_DETAIL = "student/{id}"
    fun studentDetail(id: Long) = "student/$id"

    const val GROUPS = "groups"
    const val GROUP_ADD = "group/new"
    const val GROUP_DETAIL = "group/{id}"
    fun groupDetail(id: Long) = "group/$id"

    const val CLASS_EDIT = "class/{id}?date={date}&group={group}"

    /**
     * id = 0 means "new class"; [epochDay] pre-fills the date for a new class (-1 = today).
     * [groupId] (non-zero) opens the form already booking for that group — used by "book a lesson"
     * on a group.
     */
    fun classEdit(id: Long, epochDay: Long = -1, groupId: Long = 0) =
        "class/$id?date=$epochDay&group=$groupId"
}

/**
 * The top-level tabs of the bottom bar, **in the order they appear**: who is taught first
 * («Ученики», then «Группы» right beside it — the same people seen as rosters), then when
 * («Расписание»), then the money («Отчёты») and the rest. The app still *opens* on the schedule;
 * this order is only where each tab sits. «Группы» is the one tab that can be switched off
 * (see [visible]).
 */
enum class TopDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    STUDENTS(Routes.STUDENTS, R.string.nav_students, Icons.Filled.People),
    GROUPS(Routes.GROUPS, R.string.nav_groups, Icons.Filled.Groups),
    SCHEDULE(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth),
    REPORTS(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz);

    companion object {
        /**
         * The tabs to show, given the «Группы» setting. Hiding a tab must also stop the bottom bar
         * from claiming its route as a tab (otherwise the bar would render with nothing selected
         * when that route is opened from «Ещё» instead), so the bar and its routes are both derived
         * from this one list rather than from [entries].
         */
        fun visible(showGroupsTab: Boolean): List<TopDestination> =
            entries.filter { showGroupsTab || it != GROUPS }

        fun routes(showGroupsTab: Boolean): Set<String> =
            visible(showGroupsTab).map { it.route }.toSet()
    }
}
