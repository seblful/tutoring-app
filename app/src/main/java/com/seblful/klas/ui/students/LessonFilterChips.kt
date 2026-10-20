package com.seblful.klas.ui.students

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
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
    val total = tally.values.sum()
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        KindChip(
            label = stringResource(R.string.lesson_kind_all),
            count = total,
            selected = selected == LessonFilter.ALL,
            onClick = { onSelect(LessonFilter.ALL) },
        )
        LessonKind.entries.forEach { kind ->
            val filter = LessonFilter.of(kind)
            KindChip(
                label = lessonKindLabel(kind),
                count = tally[kind] ?: 0,
                selected = selected == filter,
                onClick = { onSelect(filter) },
                // Decorative here: the chip's own label names the kind, and the icon's description on top of
                // it read «Индивидуально, Индивидуальные · 8».
                leading = { LessonKindIcon(kind, Modifier.clearAndSetSemantics { }) },
            )
        }
    }
}

@Composable
private fun KindChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(R.string.lesson_kind_count, label, count)) },
        leadingIcon = leading,
    )
}
