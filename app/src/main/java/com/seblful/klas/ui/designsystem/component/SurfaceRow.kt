package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.itemSurface
import com.seblful.klas.ui.common.rightClickAsLongPress

/**
 * One row of a list drawn as **one surface with dividers** (D3) — People's students and groups — rather than a
 * card each: forty students read as one list, not forty boxes, and fit more to a screen. The divider under every
 * row but the last starts at [dividerInset], where the text does, as Material's list dividers do.
 *
 * @param selected for the row open beside its list, tinted as the rail marks its tab, and said so.
 * @param container the list's surface: [itemSurface] for a top-level list, `sectionSurface` for rows under a
 *   [SliceHeader].
 * @param dimmed for a row whose content no longer counts — a cancelled lesson — as [ContentCard]'s.
 * @param onLongClick picks the row (R7), named for TalkBack by [onLongClickLabel]; a right click does the same.
 * @param contentPadding none for content that brings its own, as a [KlasRow] does — both put its text a gutter
 *   further in than every other card's.
 */
@Composable
fun SurfaceRow(
    position: SlicePosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dividerInset: Dp = Spacing.gutter,
    container: Color = MaterialTheme.colorScheme.itemSurface,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.gutter, vertical = RowInsetY),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = position.shapeOf(MaterialTheme.shapes.medium)
    val scheme = MaterialTheme.colorScheme
    val surface = if (selected) scheme.secondaryContainer else container
    val dividerColor = DividerDefaults.color
    CompositionLocalProvider(LocalContentColor provides scheme.contentColorFor(surface)) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(surface.copy(alpha = if (dimmed) DimmedAlpha else 1f))
                .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
                .rightClickAsLongPress(onLongClick)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
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
                .padding(contentPadding),
            content = content,
        )
    }
}

private val RowInsetY = 12.dp

private const val DimmedAlpha = 0.5f
