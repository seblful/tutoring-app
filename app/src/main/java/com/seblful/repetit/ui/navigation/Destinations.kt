package com.seblful.repetit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
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

    const val CLASS_EDIT = "class/{id}?date={date}"
    /** id = 0 means "new class"; [epochDay] pre-fills the date for a new class (-1 = today). */
    fun classEdit(id: Long, epochDay: Long = -1) = "class/$id?date=$epochDay"
}

/** The four top-level tabs shown in the bottom bar. */
enum class TopDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SCHEDULE(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth),
    STUDENTS(Routes.STUDENTS, R.string.nav_students, Icons.Filled.People),
    REPORTS(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz);

    companion object {
        val routes = entries.map { it.route }.toSet()
    }
}
