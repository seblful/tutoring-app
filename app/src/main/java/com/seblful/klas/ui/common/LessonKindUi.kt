package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind

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
