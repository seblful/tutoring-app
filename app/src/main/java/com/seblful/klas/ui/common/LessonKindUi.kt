package com.seblful.klas.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.LessonFilter
import com.seblful.klas.domain.LessonKind

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

/**
 * One colour for both kinds — the brand colour.
 *
 * They used to differ, individual muted and group in the brand colour, which made the pair read as a
 * ranking: the grey one looks like the disabled or lesser row rather than the other kind of lesson. The
 * shape already carries the distinction, and it is the only thing that has to.
 */
@Composable
private fun lessonKindColor(kind: LessonKind): Color = when (kind) {
    LessonKind.INDIVIDUAL, LessonKind.GROUP -> MaterialTheme.colorScheme.primary
}

/**
 * How tall both glyphs are actually drawn. Sizes below are derived from it, so the two stay matched.
 */
private val KindIconInk = 11.dp

/**
 * The nominal size each kind's icon is given, chosen so both are drawn to **the same ink height**.
 *
 * The two glyphs are not on the same optical scale inside their shared 24-unit viewport: `Person` is one
 * figure spanning 16 of those units vertically, while `Groups` packs three small ones into 12. Given one
 * nominal size they therefore come out a third apart — the person measurably taller, not merely
 * seemingly. Scaling each by its own fill (24/16 and 24/12) is what makes them look like one size.
 */
private fun lessonKindIconSize(kind: LessonKind): Dp = when (kind) {
    LessonKind.INDIVIDUAL -> KindIconInk * (24f / 16f)
    LessonKind.GROUP -> KindIconInk * (24f / 12f)
}

/**
 * The slot both icons sit in, wide enough for the wider of them. Fixed, so what follows the icon starts
 * at the same place down a list whichever kind each row happens to be.
 */
private val KindIconSlot = KindIconInk * (24f / 12f)

/**
 * The kind's colour when it is a **filled area** rather than an icon — the two halves of the reports
 * screen's income bar.
 *
 * Group keeps the brand colour it has beside a lesson, so the two places agree; individual takes the
 * second accent rather than the muted grey [lessonKindColor] gives it, because a segment the eye reads
 * as the bar's empty half is not a segment.
 */
@Composable
fun lessonKindFill(kind: LessonKind): Color = when (kind) {
    LessonKind.INDIVIDUAL -> MaterialTheme.colorScheme.tertiary
    LessonKind.GROUP -> MaterialTheme.colorScheme.primary
}

@Composable
fun LessonKindIcon(kind: LessonKind, modifier: Modifier = Modifier) {
    Box(modifier.size(KindIconSlot), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = lessonKindIcon(kind),
            contentDescription = lessonKindDescription(kind),
            tint = lessonKindColor(kind),
            modifier = Modifier.size(lessonKindIconSize(kind)),
        )
    }
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
