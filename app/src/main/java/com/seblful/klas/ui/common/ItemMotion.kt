package com.seblful.klas.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * `animateItem()` for a row that **moves** within its list, and **no fade either way** — the app's one
 * way to animate a lazy list item.
 */
fun LazyItemScope.animateInPlace(modifier: Modifier = Modifier): Modifier =
    modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
