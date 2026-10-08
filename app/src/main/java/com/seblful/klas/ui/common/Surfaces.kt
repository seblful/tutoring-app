package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * The two surfaces the tonal ladder is made of, and which nesting depth each belongs to.
 *
 * A **top-level browsable** list uses [ContentCard]; a list *inside* a card or a settings surface uses
 * `ListItem`. The old rule was by screen genre ("settings-style lists stay plain") and decided neither of the
 * two cases that were actually wrong — see `docs/ui-conventions.md`, *card vs. row is by nesting depth*.
 */

/**
 * A tappable surface for one **item** in a content list (a lesson, a student, a group). Gives the
 * main lists a modern, card-based look; settings-style lists keep plain rows on purpose.
 *
 * Drawn on `surfaceContainerLow` — the bottom rung of the tonal ladder (see `theme/Color.kt`) —
 * because an item and the *section* that holds items are different things and used to be the same
 * grey. Sections use [SectionCard], one step higher.
 *
 * Takes its tap through `Card`'s own `onClick` rather than a `clickable` modifier. A `clickable`
 * handed *to* the card sits outside the card's clip, so the press ripple was drawn as a hard-cornered
 * grey rectangle over the rounded corners — a grey frame flashing behind every lesson that was
 * tapped in the schedule. `CardPressFeedbackTest` keeps it that way.
 *
 * @param dimmed for an item whose content no longer counts — a cancelled lesson. It stays fully
 *   legible (it is still a record of something) but stops competing with the lessons that are on.
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}
/** How far a cancelled lesson recedes: still readable, no longer competing. */
private const val DimmedAlpha = 0.5f
/**
 * A [ContentCard]'s own vertical inset, a little tighter than [Spacing.gutter] because a card's text
 * already carries line spacing above and below it — 16 all round measures as too airy on a list row.
 */
private val CardInsetY = 12.dp

/**
 * The title over one section of a form, a card or a sheet: `titleSmall`, onSurface, said as a heading.
 *
 * The rule was written down (*Section titles* in `docs/ui-conventions.md`) and spelled out by hand at
 * each use, so it drifted the way hand-spelled rules do: the lesson sheet titled «Статус» and «Домашнее
 * задание» in grey `labelMedium` while the booking form one tap further titled «Длительность» in
 * `labelLarge` and «Участники» in `titleSmall` — three looks for one job, and two of them silent to a
 * screen reader walking by heading.
 *
 * @param color for a title that also *marks* something: the settings categories are tinted as the
 *   platform's preference categories are, the schedule's day heading as the day selected above it. The
 *   size, weight and heading role stay the same — only what the colour says differs.
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

/**
 * Money at the trailing edge of a nested row — what a taught lesson was billed.
 *
 * Material's `ListItem` sets trailing text in `labelSmall`, its smallest style, which is right for a
 * «12:40» stamp and wrong for an amount: on the student's and the group's lesson rows «40 BYN» came out
 * in 11sp, a size smaller than the status pill beside it and the smallest money in the app. Body size,
 * one rung quieter than the headline in colour, so it reads as the row's figure without outshouting the
 * date that says which lesson this is.
 */
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
 *
 * Decorative: the row is one tap target and its headline already names where it goes, so a description
 * here would have a screen reader read every row twice. A row whose headline does *not* say where it leads
 * says so with the click's own label instead. **Filled**, as the app's chevrons are: the leading glyph is
 * art for the row, the trailing one a statement about what tapping does.
 */
@Composable
fun OpensScreenMark() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A bottom sheet's handle: the pill, and nothing behind it when it is held.
 *
 * Material's sheet makes its handle a button — tap to expand or collapse — with a tooltip naming it, so
 * holding it to drag the sheet lit a hard-cornered grey block around a 32dp pill and floated an English
 * «Drag handle» over a Russian screen. Nothing else in the app shows a press as a square patch. It wraps
 * *any* `dragHandle` it is given that way, so a sheet passes `dragHandle = null` and draws this as its
 * first row. The sheet still drags (all of it does), and closes by the scrim, by back or by a fling; the
 * handle just stops being a control of its own, so it says nothing to a screen reader — the scrim's
 * «Закрыть» is the way out that reads.
 */
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

/** Material's handle measurements, so the sheet keeps the shape it had. */
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleInsetY = 22.dp
private const val HandleAlpha = 0.4f
