package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.common.ActiveFilter
import com.seblful.klas.ui.common.ActiveFilters
import com.seblful.klas.ui.common.FilterMenu
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.MenuHeading
import com.seblful.klas.ui.common.MenuOptions
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.designsystem.component.KlasDivider

/**
 * The schedule's filters — whose lessons, and which status — behind the bar's icon, so an unfiltered
 * schedule spends no row on them. Offered once there is somebody to narrow to, or while narrowed to
 * somebody since archived.
 */
@Composable
internal fun ScheduleFilterAction(
    filter: ScheduleFilter,
    students: List<Student>,
    groups: List<Group>,
    onWho: (ScheduleWho?) -> Unit,
    onStatus: (LessonStatus?) -> Unit,
) {
    if (students.isEmpty() && groups.isEmpty() && filter.who == null) return
    val options =
        students.map { ScheduleWho.Student(it.id, it.name) } + groups.map { ScheduleWho.Group(it.id, it.name) }
    FilterMenu(stringResource(R.string.schedule_filter)) { close ->
        MenuHeading(stringResource(R.string.schedule_filter_who))
        TickedMenuItem(
            text = stringResource(R.string.schedule_filter_everyone),
            selected = filter.who == null,
            onClick = { close(); onWho(null) },
        )
        MenuOptions(options, ScheduleWho::name) { option ->
            TickedMenuItem(
                text = option.name,
                selected = option == filter.who,
                onClick = { close(); onWho(option) },
                leadingIcon = {
                    LessonKindIcon(if (option is ScheduleWho.Group) LessonKind.GROUP else LessonKind.INDIVIDUAL)
                },
            )
        }
        KlasDivider()
        MenuHeading(stringResource(R.string.schedule_filter_status))
        TickedMenuItem(
            text = stringResource(R.string.schedule_filter_any_status),
            selected = filter.status == null,
            onClick = { close(); onStatus(null) },
        )
        LessonStatus.entries.forEach { option ->
            TickedMenuItem(
                text = classStatusLabel(option),
                selected = option == filter.status,
                onClick = { close(); onStatus(option) },
            )
        }
    }
}

/**
 * Under the pager: what the schedule is narrowed to, a chip each — **what makes the filter honest**, since a
 * narrowed schedule and a quiet week look alike — then [trailing] (the Day view's summary). Neither, and
 * nothing is drawn.
 */
@Composable
internal fun ScheduleFilterRow(
    filter: ScheduleFilter,
    onWho: (ScheduleWho?) -> Unit,
    onStatus: (LessonStatus?) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val active = listOfNotNull(
        filter.who?.let { ActiveFilter(it.name) { onWho(null) } },
        filter.status?.let { ActiveFilter(classStatusLabel(it)) { onStatus(null) } },
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        ActiveFilters(active)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
