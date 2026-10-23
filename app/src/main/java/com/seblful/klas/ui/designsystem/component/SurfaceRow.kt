package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.itemSurface

/**
 * One row of a list drawn as **one surface with dividers** (D3) — People's students and groups — rather than a
 * card each: forty students read as one list, not forty boxes, and fit more to a screen. The divider under every
 * row but the last starts at [dividerInset], where the text does, as Material's list dividers do.
 *
 * @param selected for the row open beside its list, tinted as the rail marks its tab, and said so.
 */
@Composable
fun SurfaceRow(
    position: SlicePosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dividerInset: Dp = Spacing.gutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = MaterialTheme.shapes.medium
    val square = CornerSize(0.dp)
    val shape = card.copy(
        topStart = if (position.roundsTop) card.topStart else square,
        topEnd = if (position.roundsTop) card.topEnd else square,
        bottomStart = if (position.roundsBottom) card.bottomStart else square,
        bottomEnd = if (position.roundsBottom) card.bottomEnd else square,
    )
    val scheme = MaterialTheme.colorScheme
    val surface = if (selected) scheme.secondaryContainer else scheme.itemSurface
    val dividerColor = DividerDefaults.color
    CompositionLocalProvider(LocalContentColor provides scheme.contentColorFor(surface)) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(surface)
                .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
                .clickable(onClick = onClick)
                // Drawn over the row's bottom edge rather than laid out under it, so every row of a list is
                // one height whether it has a divider or is the last.
                .drawWithContent {
                    drawContent()
                    if (!position.roundsBottom) {
                        val stroke = DividerDefaults.Thickness.toPx()
                        val y = size.height - stroke / 2
                        drawLine(dividerColor, Offset(dividerInset.toPx(), y), Offset(size.width, y), stroke)
                    }
                }
                .padding(horizontal = Spacing.gutter, vertical = RowInsetY),
            content = content,
        )
    }
}

private val RowInsetY = 12.dp
