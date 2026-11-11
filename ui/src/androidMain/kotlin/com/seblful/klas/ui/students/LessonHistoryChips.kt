package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.lessonKindLabel
import com.seblful.klas.ui.designsystem.component.FilterBar
import com.seblful.klas.ui.designsystem.component.FilterOption

/**
 * The lesson history's chips: Planned, Taught and Cancelled, each with its count and each tapped again to let
 * go — then, for a student taught both ways, the kinds, which [FilterBar] folds behind its menu.
 *
 * @param statuses how many lessons have each status; one with none still shows its 0, so the row holds still.
 */
@Composable
fun LessonHistoryChips(
    status: LessonStatus?,
    statuses: Map<LessonStatus, Int>,
    onStatus: (LessonStatus?) -> Unit,
    kind: LessonFilter,
    kinds: Map<LessonKind, Int>?,
    onKind: (LessonFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byStatus = HistoryStatuses.map { shown ->
        FilterOption(
            label = stringResource(Strings.lesson_kind_count, classStatusLabel(shown), statuses[shown] ?: 0),
            selected = status == shown,
            onClick = { onStatus(shown.takeIf { status != it }) },
        )
    }
    val byKind = kinds?.let { tally ->
        LessonKind.entries.map { each ->
            val filter = LessonFilter.of(each)
            FilterOption(
                label = stringResource(Strings.lesson_kind_count, lessonKindLabel(each), tally[each] ?: 0),
                selected = kind == filter,
                onClick = { onKind(if (kind == filter) LessonFilter.ALL else filter) },
            )
        }
    }.orEmpty()
    FilterBar(byStatus + byKind, modifier.padding(horizontal = Spacing.gutter))
}

private val HistoryStatuses = listOf(LessonStatus.SCHEDULED, LessonStatus.COMPLETED, LessonStatus.CANCELLED)
