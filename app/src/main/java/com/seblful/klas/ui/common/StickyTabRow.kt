package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A band that **stays** while a list scrolls under it — so it is drawn as one.
 *
 * The detail cards' tab rows were painted `colorScheme.surface`, which is the same value as the page
 * under them, so a row pinned by `stickyHeader` read as part of the scroll: the rows travelling beneath
 * appeared to slide out from behind nothing, and at the moment the first one reached the band there was
 * no edge for it to disappear behind. The tonal ladder the app already declares has the answer one rung
 * up (`surfaceContainer`, the *section* rung — see `ContentCard`), and a divider at the base draws the
 * edge itself.
 *
 * **What earns it is that content genuinely passes underneath.** «Люди»'s students-or-groups control had
 * this too, on the argument that a fixed band is a fixed band however it came to be fixed — but that
 * control sits *outside* the lists rather than over them, so each list is clipped at its own top edge a
 * few dp below and nothing ever travels under the segments. The band was drawing an edge nothing crosses,
 * at the price of making the app's two segmented controls look like different controls on the two screens
 * most likely to be compared. It is the schedule's plain treatment on both now.
 *
 * So this stays separate from [StickyTabRow] for what it *is* — the band, apart from what is standing in
 * it — rather than for having a second caller.
 */
@Composable
fun PinnedHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        content()
        HorizontalDivider()
    }
}

/** The tab row pinned to the top of a detail card's list — a [PinnedHeader] holding tabs. */
@Composable
fun StickyTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    PinnedHeader(modifier) {
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            // The row's own container would be `surface` again; the [PinnedHeader] above is what
            // carries the colour, so this stays transparent rather than repainting the page's value
            // over it.
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tabs = tabs,
        )
    }
}
