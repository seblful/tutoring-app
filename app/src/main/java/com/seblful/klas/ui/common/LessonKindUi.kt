package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import com.seblful.klas.ui.theme.KlasIcons

/*
 * How the two kinds of lesson are told apart on screen — one icon, one word, one chip row, shared by
 * every list that shows lessons, so a group lesson looks the same wherever it turns up.
 */

/** Plural label — «Индивидуальные» / «Групповые». Names a set of lessons, not one. */
@Composable
fun lessonKindLabel(kind: LessonKind): String = stringResource(
    when (kind) {
        LessonKind.INDIVIDUAL -> R.string.lesson_kind_individual
        LessonKind.GROUP -> R.string.lesson_kind_group
    },
)

@Composable
private fun lessonKindDescription(kind: LessonKind): String = stringResource(
    when (kind) {
        LessonKind.INDIVIDUAL -> R.string.lesson_kind_individual_one
        LessonKind.GROUP -> R.string.class_target_group
    },
)

private fun lessonKindIcon(kind: LessonKind): ImageVector = when (kind) {
    LessonKind.INDIVIDUAL -> KlasIcons.Filled.Person
    LessonKind.GROUP -> KlasIcons.Filled.Groups
}

@Composable
private fun lessonKindColor(kind: LessonKind): Color = when (kind) {
    LessonKind.INDIVIDUAL, LessonKind.GROUP -> MaterialTheme.colorScheme.primary
}

private val KindIconInk = 11.dp

private fun lessonKindIconSize(kind: LessonKind): Dp = when (kind) {
    LessonKind.INDIVIDUAL -> KindIconInk * (24f / 16f)
    LessonKind.GROUP -> KindIconInk * (24f / 12f)
}

private val KindIconSlot = KindIconInk * (24f / 12f)

/**
 * The kind's colour when it is a **filled area** rather than an icon — the two halves of the reports
 * screen's income bar.
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
