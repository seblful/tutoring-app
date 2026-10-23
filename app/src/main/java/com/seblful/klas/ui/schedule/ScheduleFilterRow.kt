package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Arrangement
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
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.MenuOptions
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.component.MenuChip

/**
 * The schedule's filters as chips under its pager — whose lessons, and which status — with [trailing] (the
 * Day view's summary) beside them, so the header keeps one height whether the schedule is narrowed or not
 * (R5). A narrowed chip is tinted and names what it narrows to: a narrowed schedule and a quiet week must
 * not look alike.
 */
@Composable
internal fun ScheduleFilterRow(
    filter: ScheduleFilter,
    students: List<Student>,
    groups: List<Group>,
    onWho: (ScheduleWho?) -> Unit,
    onStatus: (LessonStatus?) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Offered once there is somebody to narrow to, or while narrowed to somebody since archived.
        if (students.isNotEmpty() || groups.isNotEmpty() || filter.who != null) {
            WhoChip(filter.who, students, groups, onWho)
            StatusChip(filter.status, onStatus)
        }
        trailing()
    }
}

@Composable
private fun WhoChip(who: ScheduleWho?, students: List<Student>, groups: List<Group>, onWho: (ScheduleWho?) -> Unit) {
    val options =
        students.map { ScheduleWho.Student(it.id, it.name) } + groups.map { ScheduleWho.Group(it.id, it.name) }
    MenuChip(label = who?.name ?: stringResource(R.string.schedule_filter_everyone), narrowed = who != null) { close ->
        TickedMenuItem(
            text = stringResource(R.string.schedule_filter_everyone),
            selected = who == null,
            onClick = { close(); onWho(null) },
        )
        MenuOptions(options, ScheduleWho::name) { option ->
            TickedMenuItem(
                text = option.name,
                selected = option == who,
                onClick = { close(); onWho(option) },
                leadingIcon = {
                    LessonKindIcon(if (option is ScheduleWho.Group) LessonKind.GROUP else LessonKind.INDIVIDUAL)
                },
            )
        }
    }
}

@Composable
private fun StatusChip(status: LessonStatus?, onStatus: (LessonStatus?) -> Unit) {
    MenuChip(
        label = status?.let { classStatusLabel(it) } ?: stringResource(R.string.schedule_filter_any_status),
        narrowed = status != null,
    ) { close ->
        TickedMenuItem(
            text = stringResource(R.string.schedule_filter_any_status),
            selected = status == null,
            onClick = { close(); onStatus(null) },
        )
        KlasDivider()
        LessonStatus.entries.forEach { option ->
            TickedMenuItem(
                text = classStatusLabel(option),
                selected = option == status,
                onClick = { close(); onStatus(option) },
            )
        }
    }
}
