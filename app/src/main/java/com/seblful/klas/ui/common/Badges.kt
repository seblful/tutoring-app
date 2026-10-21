package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.theme.klasColors

/*
 * How the app says what state a thing is in: a pill, never a font style.
 *
 * «в архиве» used to be an italic grey word — the one place a state was said in emphasis rather than in the
 * shape the lesson statuses use, and emphasis carries nothing to a screen reader.
 */

/** A small tonal pill — e.g. a class status. The tint is derived from [color]. */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    // Judged on the highest card surface a pill sits on: every lower one leaves it more contrast.
    val pill = color.copy(alpha = PillAlpha).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = PillAlpha))
            .padding(horizontal = Spacing.inline, vertical = Spacing.tight),
    ) {
        Text(
            text,
            // labelSmall, not labelMedium. The pill is the secondary half of its row and the name
            // beside it is the primary one, so when the two compete for a phone's width the pill is
            // what gives — a status read at 11sp is still read; a name cut short is not.
            style = MaterialTheme.typography.labelSmall,
            color = readableOn(pill, color, klasColors.isDark),
            maxLines = 1,
        )
    }
}

/** [color], deepened (lightened after dark) only as far as text needs to read on [background]. */
internal fun readableOn(background: Color, color: Color, dark: Boolean): Color {
    val toward = if (dark) Color.White else Color.Black
    var step = 0
    var text = color
    while (contrastRatio(text, background) < TextContrast && step < Steps) {
        step++
        text = lerp(color, toward, step / Steps.toFloat())
    }
    return text
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + LuminanceFlare) / (dark + LuminanceFlare)
}

/** WCAG's allowance for ambient light on a screen, added to both luminances. */
private const val LuminanceFlare = 0.05f
private const val PillAlpha = 0.15f
private const val TextContrast = 4.5f
private const val Steps = 20

/** «в архиве» — a retired student, a retired group, or a member who has left. */
@Composable
fun ArchivedBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = stringResource(R.string.student_archived_badge),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A pill and the words beside it, set as **one line of text**: both on a shared baseline, so the line has
 * one. Centred on each other they sat on two, and `ListItem` reads a supporting slot whose first and last
 * baselines differ as a second line — it laid two-line rows out as three-line ones, 88 dp tall with their
 * icon pinned to the top.
 *
 * @param badgeFirst the pill before the words (a lesson's status) or after them (a member's «в архиве»).
 */
@Composable
fun BadgeLine(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    badgeFirst: Boolean = true,
    text: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        if (badgeFirst) Box(Modifier.alignByBaseline()) { badge() }
        // Shrinks before the pill does: the words are the half that can be cut.
        Box(Modifier.alignByBaseline().weight(1f, fill = false)) { text() }
        if (!badgeFirst) Box(Modifier.alignByBaseline()) { badge() }
    }
}
