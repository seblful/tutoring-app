package com.seblful.klas.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * [content] with its [name] in a bubble **while it is held** — Material's plain tooltip.
 *
 * For the bars' glyphs: an icon at the top of a screen says what it does to somebody who already knows,
 * and holding it is how anybody else asks — the answer every Material app gives, so a thumb expects it.
 * The bottom bar's tabs have it too, their words being the smallest type on the screen.
 *
 * Only a hold shows it: a tap still reaches [content] as it did, since the box watches the press
 * without taking it until the hold has lasted.
 *
 * @param above for a control at the foot of the screen, whose bubble would otherwise open under the thumb
 *   holding it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldToName(name: String, above: Boolean = false, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * An icon button that **names itself when held** — the shape every glyph in a bar takes.
 *
 * Its [name] is said twice, once in the bubble and once to a screen reader, and the two were written out
 * by hand at a dozen call sites; one place to say it is what keeps the bubble and the description from
 * drifting into two names for one button.
 *
 * @param tint an override for the glyph's colour — the accent where a button marks something (a list in
 *   a non-default order, a send action). Null keeps the button's own, which is what dims it when disabled.
 */
@Composable
fun NamedIconButton(
    icon: ImageVector,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    HoldToName(name) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            if (tint != null) Icon(icon, name, tint = tint) else Icon(icon, name)
        }
    }
}
