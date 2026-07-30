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
 * The app has two: a detail card's tab row ([StickyTabRow]) and «Люди»'s students-or-groups control.
 * Both were painted `colorScheme.surface`, which is the same value as the page under them, so a fixed
 * band read as part of the scroll: the rows travelling beneath appeared to slide out from behind
 * nothing, and at the moment the first one reached the band there was no edge for it to disappear
 * behind. The tonal ladder the app already declares has the answer one rung up (`surfaceContainer`,
 * the *section* rung — see `ContentCard`), and a divider at the base draws the edge itself.
 *
 * The two arrived at it differently, which is why this is one component rather than a rule stated
 * twice: the tab rows were pinned by `stickyHeader` and looked wrong, and People's segmented control
 * never scrolled at all — it sits above the list, outside it — and looked exactly as wrong for the
 * same reason. What makes a band need this is that content passes under it, not how it came to be
 * fixed there.
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
