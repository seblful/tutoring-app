package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
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

/** The bar's title: the screen's own, or how many are picked while any are. */
@Composable
fun titleOrPicked(title: String, picked: Int): String =
    if (picked == 0) title else pluralStringResource(R.plurals.batch_selected, picked, picked)
