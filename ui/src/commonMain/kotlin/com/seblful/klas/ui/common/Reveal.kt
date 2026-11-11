package com.seblful.klas.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.seblful.klas.ui.theme.KlasMotion

/**
 * Something that comes and goes inside a column — a fold's fields, a price under its ticked subject — growing
 * down from its top as it fades in, and folding away the same way, so what is under it moves rather than jumps.
 *
 * Shown from the first frame it is composed visible: only a change animates.
 */
@Composable
fun ColumnScope.Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = expandVertically(KlasMotion.defaultSpatial(), expandFrom = Alignment.Top) +
            fadeIn(KlasMotion.defaultEffects()),
        exit = shrinkVertically(KlasMotion.defaultSpatial(), shrinkTowards = Alignment.Top) +
            fadeOut(KlasMotion.defaultEffects()),
        content = content,
    )
}
