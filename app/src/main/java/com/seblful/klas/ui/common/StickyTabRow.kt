package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The tab row pinned to the top of a detail card's list.
 *
 * **It is a fixed header, so it has to look like one.** Both cards painted it
 * `colorScheme.surface` — which is the same value as the page under it — so a pinned row read as part
 * of the scroll: the rows travelling beneath appeared to slide out from behind nothing, and at the
 * moment the first one reached it there was no edge for it to disappear behind. The tonal ladder the
 * app already declares has the answer one rung up (`surfaceContainer`, the *section* rung — see
 * `ContentCard`), and a divider at the base draws the edge itself.
 *
 * Shared rather than written twice because the two cards had already written it twice, identically,
 * and both were wrong in the same way — which is what a shared component is for.
 */
@Composable
fun StickyTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            // The row's own container would be `surface` again; the Column above is what carries the
            // colour, so this stays transparent rather than repainting the page's value over it.
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tabs = tabs,
        )
        HorizontalDivider()
    }
}
