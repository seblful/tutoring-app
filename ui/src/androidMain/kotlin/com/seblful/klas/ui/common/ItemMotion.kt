package com.seblful.klas.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.ui.Modifier
import com.seblful.klas.ui.theme.KlasMotion

/**
 * `animateItem()` for a row that **moves** within its list, and **no fade either way** — the app's one
 * way to animate a lazy list item.
 */
fun LazyItemScope.animateInPlace(modifier: Modifier = Modifier): Modifier =
    modifier.animateItem(fadeInSpec = null, placementSpec = KlasMotion.defaultSpatial(), fadeOutSpec = null)

/** [animateInPlace] for a grid's item. */
fun LazyGridItemScope.animateInPlace(modifier: Modifier = Modifier): Modifier =
    modifier.animateItem(fadeInSpec = null, placementSpec = KlasMotion.defaultSpatial(), fadeOutSpec = null)
