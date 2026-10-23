package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.seblful.klas.R

/** Back lets go of a pick made by a long-press (R7) — before it leaves the screen. */
@Composable
fun PickBackHandler(picked: Int, onClear: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = picked > 0,
        onBackCompleted = onClear,
    )
}

/**
 * A pick under way (R7), for [KlasScreen]'s bar to become its selection bar while [count] is above none: how
 * many, said again as it changes; ✕ — and Back — to let go ([onClear]); and [actions] on what is picked, in
 * place of the screen's own. The add button waits meanwhile.
 */
class Selection(val count: Int, val onClear: () -> Unit, val actions: @Composable RowScope.() -> Unit)

/** «3 selected», a polite live region: a count that changes under a finger is worth hearing. */
@Composable
internal fun SelectionTitle(count: Int) {
    Text(
        pluralStringResource(R.plurals.batch_selected, count, count),
        maxLines = 1,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
