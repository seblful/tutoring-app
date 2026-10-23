package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.lessonKindLabel
import com.seblful.klas.ui.designsystem.component.FilterBar
import com.seblful.klas.ui.designsystem.component.FilterOption

/**
 * Narrows a list holding both kinds of lesson, and says how many of each there are while doing it —
 * so the split is legible without selecting anything, which is the question a mixed list raises first.
 *
 * @param tally how many lessons each kind has; a kind with none shows a 0 rather than vanishing,
 * because a chip row whose chips come and go cannot be aimed at.
 */
@Composable
fun LessonFilterChips(
    selected: LessonFilter,
    tally: Map<LessonKind, Int>,
    onSelect: (LessonFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = FilterOption(
        label = stringResource(
            R.string.lesson_kind_count,
            stringResource(R.string.lesson_kind_all),
            tally.values.sum(),
        ),
        selected = selected == LessonFilter.ALL,
        onClick = { onSelect(LessonFilter.ALL) },
    )
    val kinds = LessonKind.entries.map { kind ->
        val filter = LessonFilter.of(kind)
        FilterOption(
            label = stringResource(R.string.lesson_kind_count, lessonKindLabel(kind), tally[kind] ?: 0),
            selected = selected == filter,
            onClick = { onSelect(filter) },
            // Decorative here: the chip's own label names the kind, and the icon's description on top of it
            // read «Индивидуально, Индивидуальные · 8».
            leading = { LessonKindIcon(kind, Modifier.clearAndSetSemantics { }) },
        )
    }
    FilterBar(listOf(all) + kinds, modifier.padding(horizontal = Spacing.gutter))
}
