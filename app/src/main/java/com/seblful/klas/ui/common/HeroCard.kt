package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **The one number a screen exists to answer**, in the loudest colour the palette has.
 *
 * The report's income card was a genuinely good moment and the app's *only* filled accent surface, so it
 * read as a one-off rather than as a device the system owns. Its natural sibling — the student card's
 * balance, the answer to "does this person owe me money" — was plain text in red inside a grey stat
 * tile, and the two most important numbers in the app were presented as if only one of them mattered.
 *
 * **Two uses, and no more.** A filled `primaryContainer` earns its volume by being rare: a third one
 * would make this a card style rather than an emphasis, and then the report's total would be competing
 * with something. If a screen wants a hero and does not have one, the honest answer is usually that its
 * headline number is not that important.
 *
 * @param label what the number is, above it and quiet — the number is the thing being read.
 * @param value already formatted, currency and all. `headlineMedium`, which is the largest type in the
 *   app and is spent here.
 * @param tonal for a hero whose subject is **neutral or bad news** — a debt. `primaryContainer` says
 *   "here is your income"; the same fill under «Долг 240» would say it about money the tutor has not
 *   been paid. A secondary container keeps the shape and drops the celebration.
 * @param detail an optional line under the number: the comparison against the previous period, what the
 *   balance means in words. It inherits the card's on-container colour, so it needs no colour of its own.
 */
@Composable
fun HeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    detail: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tonal) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (tonal) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(HeroInset),
            // The label, the number and the detail are one statement on three lines, not three things.
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            // One line, shrinking a step at a time: «12 450,50 BYN» at a large font scale wrapped the
            // currency onto a line of its own, which is the one number on the screen broken in two.
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = HeroMinFontSize,
                    maxFontSize = MaterialTheme.typography.headlineMedium.fontSize,
                ),
            )
            detail?.invoke(this)
        }
    }
}

/**
 * Wider than [Spacing.gutter], deliberately: this is the one card on a screen that is allowed to feel
 * generous, and 16 all round around 28sp type reads as cramped.
 */
private val HeroInset = 20.dp

/** The smallest the hero number shrinks to — still the largest text on its screen. */
private val HeroMinFontSize = 20.sp
