package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The two surfaces the tonal ladder is made of, and which nesting depth each belongs to.
 *
 * A **top-level browsable** list uses [ContentCard]; a list *inside* a card or a settings surface uses
 * `ListItem`. The old rule was by screen genre ("settings-style lists stay plain") and decided neither of the
 * two cases that were actually wrong — see `docs/ui-conventions.md`, *card vs. row is by nesting depth*.
 */

/**
 * The tone an **item** card is drawn in — [ContentCard]. Two rungs above `surfaceContainer`, which is what
 * the bar tints to on scroll: a card sliding under it must not melt into it. M3's own filled card sits
 * one rung higher still.
 */
val ColorScheme.itemSurface: Color get() = surfaceContainerHigh

/** One rung above [itemSurface]: a **section** holding rows — [SectionCard], the stat strip, a report. */
val ColorScheme.sectionSurface: Color get() = surfaceContainerHighest

/**
 * A tappable surface for one **item** in a content list (a lesson, a student, a group).
 *
 * @param dimmed for an item whose content no longer counts — a cancelled lesson. It stays fully
 * legible (it is still a record of something) but stops competing with the lessons that are on.
 */
@Composable
fun ContentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.itemSurface
                .copy(alpha = if (dimmed) DimmedAlpha else 1f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = CardInsetY),
            content = content,
        )
    }
}
/**
 * A surface for a **section** — a group of rows read as one thing: the stat strip, a report's
 * breakdown. One rung above [ContentCard] on the tonal ladder, which is what gives a screen holding
 * both a legible hierarchy instead of one flat grey.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.sectionSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

private const val DimmedAlpha = 0.5f

private val CardInsetY = 12.dp

/**
 * The title over one section of a form, a card or a sheet: `titleSmall`, onSurface, said as a heading.
 *
 * @param color for a title that also *marks* something — the schedule's day heading, tinted as the day
 * selected above it. The size, weight and heading role stay the same — only what the colour says differs.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/** oney at the trailing edge of a nested row — what a taught lesson was billed. */
@Composable
fun TrailingAmount(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * The trailing mark of a row that **opens a screen** of the app — «Ещё»'s menu, Settings' diagnostics, a
 * group's member.
 */
@Composable
fun OpensScreenMark() {
    Icon(KlasIcons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**  bottom sheet's handle: the pill, and nothing behind it when it is held. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = HandleInsetY)
            .size(width = HandleWidth, height = HandleHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HandleAlpha))
            .clearAndSetSemantics { },
    )
}

private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleInsetY = 22.dp
private const val HandleAlpha = 0.4f
