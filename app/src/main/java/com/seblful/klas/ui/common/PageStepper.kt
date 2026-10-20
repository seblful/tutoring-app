package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.seblful.klas.ui.theme.KlasIcons

/**
 * ‹ [centre] › — something read **one page at a time**, with a step to either side of it: the schedule's
 * day, week and month, the report's period.
 *
 * @param enabled whether there is anywhere to step to. False disables the arrows rather than hiding them,
 * so the row keeps its shape and the title its place («Всё время» has no neighbours).
 */
@Composable
fun PageStepper(
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centre: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = enabled) { Icon(KlasIcons.Filled.ChevronLeft, previousDescription) }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = centre,
        )
        IconButton(onClick = onNext, enabled = enabled) { Icon(KlasIcons.Filled.ChevronRight, nextDescription) }
    }
}
