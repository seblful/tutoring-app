package com.seblful.klas.ui.common

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a full-width row shows it is being pressed: a soft **rounded** tint inset from the edges, never a
 * flat grey band from one side of the screen to the other.
 *
 * The cards already pressed that way (`ContentCard`, by its own shape) and so did the contact rows and the
 * report's debts (clipped to `shapes.small`), while every `ListItem`, sheet action and settings row lit a
 * hard-cornered grey block the full width of the phone — the same press, two looks, and the blocky one
 * on the surfaces a tutor holds most. This is the rounded one, for rows.
 *
 * The **target** is still the whole row: [press] is applied to it as given, its interaction source handed
 * in so it can pass `indication = null` and leave the drawing here. Only the ripple is inset, so a tap in
 * the row's outer [RowPressInset] still lands; and the row's content is laid out where it was ([bleed]),
 * so its text stays on the gutter rather than stepping in by the inset.
 *
 * ```
 * ListItem(modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = open) })
 * ```
 */
@Composable
fun Modifier.insetRowPress(press: (MutableInteractionSource) -> Modifier): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .then(press(source))
        .padding(horizontal = RowPressInset)
        .clip(MaterialTheme.shapes.small)
        .indication(source, ripple())
        .bleed(RowPressInset)
}

/**
 * [insetRowPress] for a row whose content starts **flush** with its column — a radio or a tick at the very
 * edge of a dialog's or a form's content. Insetting the shape there would cut through the control, so the
 * shape reaches *out* by the same distance into the padding around the column instead, and so does the
 * target. The content stays where it was.
 */
@Composable
fun Modifier.outsetRowPress(press: (MutableInteractionSource) -> Modifier): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .bleed(RowPressInset)
        .then(press(source))
        .clip(MaterialTheme.shapes.small)
        .indication(source, ripple())
        .padding(horizontal = RowPressInset)
}

/**
 * Lets content reach [by] past its parent on either side while laying it out where it would have been
 * — so a ripple can spread into a card's padding (the report's debts), or a row's content can keep its
 * full width inside an inset press shape ([insetRowPress]), without moving the text off its edge.
 */
fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val wider = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/** How far a row's pressed shape sits in from its edges ([insetRowPress]) or out past them ([outsetRowPress]). */
private val RowPressInset = 8.dp
