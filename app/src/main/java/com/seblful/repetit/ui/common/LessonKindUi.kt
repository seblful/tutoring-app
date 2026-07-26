package com.seblful.repetit.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seblful.repetit.R
import com.seblful.repetit.domain.LessonFilter
import com.seblful.repetit.domain.LessonKind

/**
 * How the two kinds of lesson are told apart on screen — one icon, one word, one chip row, shared by
 * every list that shows lessons, so a group lesson looks the same wherever it turns up.
 */

/** Plural label — «Индивидуальные» / «Групповые». Names a set of lessons, not one. */
@Composable
fun lessonKindLabel(kind: LessonKind): String = stringResource(
    when (kind) {
        LessonKind.INDIVIDUAL -> R.string.lesson_kind_individual
        LessonKind.GROUP -> R.string.lesson_kind_group
    }
)

/** Singular, for the icon standing next to **one** lesson — «Индивидуально» / «Группа». */
@Composable
private fun lessonKindDescription(kind: LessonKind): String = stringResource(
    when (kind) {
        LessonKind.INDIVIDUAL -> R.string.lesson_kind_individual_one
        LessonKind.GROUP -> R.string.class_target_group
    }
)

/**
 * The icon for a kind: a person, or a group of them.
 *
 * Both kinds get one. Marking only the group ones — as the schedule did — makes the distinction
 * readable *once you know* the icon means something, and invisible until then: a lone individual
 * lesson looks like a lesson with a missing icon rather than an individual one.
 */
private fun lessonKindIcon(kind: LessonKind): ImageVector = when (kind) {
    LessonKind.INDIVIDUAL -> Icons.Filled.Person
    LessonKind.GROUP -> Icons.Filled.Groups
}

/** Muted for an individual lesson, the brand colour for a group's — the rarer, busier thing. */
@Composable
private fun lessonKindColor(kind: LessonKind): Color = when (kind) {
    LessonKind.INDIVIDUAL -> MaterialTheme.colorScheme.onSurfaceVariant
    LessonKind.GROUP -> MaterialTheme.colorScheme.primary
}

@Composable
fun LessonKindIcon(kind: LessonKind, modifier: Modifier = Modifier) {
    Icon(
        imageVector = lessonKindIcon(kind),
        contentDescription = lessonKindDescription(kind),
        tint = lessonKindColor(kind),
        modifier = modifier.size(20.dp),
    )
}

/**
 * Narrows a list holding both kinds of lesson, and says how many of each there are while doing it —
 * so the split is legible without selecting anything, which is the question a mixed list raises first.
 *
 * Scrolls sideways rather than wrapping: three counted labels are wider than a phone at a large font
 * scale, and a chip row that reflows to two lines pushes the list it belongs to off the screen.
 *
 * @param tally how many lessons each kind has; a kind with none shows a 0 rather than vanishing,
 *   because a chip row whose chips come and go cannot be aimed at.
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
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                leading = { LessonKindIcon(kind) },
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
