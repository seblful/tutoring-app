package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The two surfaces the tonal ladder is made of, and which nesting depth each belongs to.
 *
 * A **top-level browsable** list uses [ContentCard]; a list *inside* a card or a settings surface uses
 * `ListItem`. The old rule was by screen genre ("settings-style lists stay plain") and decided neither of the
 * two cases that were actually wrong — see CONTEXT.md, *a card, or a row, by nesting depth*.
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
