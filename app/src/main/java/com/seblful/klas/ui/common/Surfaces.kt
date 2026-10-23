@file:Suppress("ForbiddenImport") // ContentCard and SectionCard are the design system's cards.

package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
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
 * @param selected for the item open beside its list, tinted as the rail marks its tab.
 * @param onLongClick picks the item (R7: a long-press only ever selects), named for TalkBack by [onLongClickLabel].
 */
@Composable
fun ContentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.itemSurface
    val shape = CardDefaults.shape
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel),
        // The content colour is named: a translucent container matches no role for `contentColorFor` to find.
        colors = CardDefaults.cardColors(
            containerColor = surface.copy(alpha = if (dimmed) DimmedAlpha else 1f),
            contentColor = MaterialTheme.colorScheme.contentColorFor(surface),
        ),
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

/** Money at the trailing edge of a nested row — what a taught lesson was billed. */
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
fun OpensScreenMark(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(KlasIcons.Filled.ChevronRight, contentDescription = null, tint = tint)
}

/**
 * A bottom sheet's handle: the pill, and nothing behind it when it is held.
 *
 * @param onDismiss closes the sheet for a screen reader, the action Material's own handle carries.
 */
@Composable
fun SheetHandle(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val handleLabel = stringResource(R.string.sheet_drag_handle)
    val dismissLabel = stringResource(R.string.sheet_dismiss)
    Box(
        modifier
            .padding(vertical = HandleInsetY)
            .size(width = HandleWidth, height = HandleHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HandleAlpha))
            .clearAndSetSemantics {
                if (onDismiss != null) {
                    contentDescription = handleLabel
                    dismiss(dismissLabel) { onDismiss(); true }
                }
            },
    )
}

private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleInsetY = 22.dp
private const val HandleAlpha = 0.4f
