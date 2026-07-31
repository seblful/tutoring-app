package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * How the app says what state a thing is in: a pill, never a font style.
 *
 * «в архиве» used to be an italic grey word — the one place a state was said in emphasis rather than in the
 * shape the lesson statuses use, and emphasis carries nothing to a screen reader.
 */

/**
 * A small tonal pill — e.g. a class status. The tint is derived from [color].
 *
 * Kept deliberately tight (8dp of side padding, one line) because of where it sits: on a lesson card
 * it shares a row with the lesson's *name*, and every dp the pill takes is a dp the name loses. A
 * status is a two-word fact; the name is the thing being read.
 */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.inline, vertical = Spacing.tight),
    ) {
        Text(
            text,
            // labelSmall, not labelMedium. The pill is the secondary half of its row and the name
            // beside it is the primary one, so when the two compete for a phone's width the pill is
            // what gives — a status read at 11sp is still read; a name cut short is not.
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
