package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** The list keys [detailCard] gives its own rows; a card's other rows bring theirs. */
object DetailCard {
    const val STATS_KEY = "stats"
    const val VIEWS_KEY = "tabs"
}

/**
 * A student's or a group's card under its bar: [identity], then the [stats], then the pinned view menu,
 * then the [view] it has selected.
 */
fun <T> LazyListScope.detailCard(
    identity: LazyListScope.() -> Unit,
    stats: @Composable () -> List<Stat>,
    views: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    count: (T) -> Int?,
    view: LazyListScope.(T) -> Unit,
) {
    identity()
    item(key = DetailCard.STATS_KEY) {
        // Parts of one thing — the numbers qualify the identity above them — so [Spacing.inline].
        StatRow(
            stats = stats(),
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
        )
    }
    // Pinned, so the other views stay a tap away however long the one under it runs.
    stickyHeader(key = DetailCard.VIEWS_KEY) {
        StickyViewMenu(
            options = views,
            selected = selected,
            onSelect = onSelect,
            label = label,
            icon = icon,
            count = count,
        )
    }
    view(selected)
}
