package com.seblful.klas.ui.schedule.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.Reveal
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasMotion

/*
 * The booking form's folded rows — Length, Repeat, Price, Format: one line saying what each is set to, opened in
 * place by a tap on it.
 */

/**
 * A line with [label] and what it is set to — [summary], red when [wanting] — that a tap opens to [content]
 * and closes again. Whether it is [opened] is the caller's, so a card of them can keep one open at a time. It is pressed as the app's rows are, a rounded tint rather than a grey band
 * ([outsetRowPress]); it opens by growing downwards, the summary fading as the fields it sums up appear,
 * and its chevron turns to say which way the next tap goes.
 */
@Composable
internal fun Fold(
    label: @Composable () -> Unit,
    summary: String,
    opened: Boolean,
    onToggle: (Boolean) -> Unit,
    wanting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(
        if (opened) HALF_TURN else 0f,
        animationSpec = KlasMotion.defaultSpatial(),
        label = "fold chevron",
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(FOLD_TAG)
                .outsetRowPress { source ->
                    Modifier.clickable(source, indication = null, role = Role.Button) { onToggle(!opened) }
                }
                .semantics {
                    if (opened) collapse { onToggle(false); true } else expand { onToggle(true); true }
                }
                .heightIn(min = MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        ) {
            Box(Modifier.weight(1f)) { label() }
            AnimatedVisibility(
                !opened,
                enter = fadeIn(KlasMotion.defaultEffects()),
                exit = fadeOut(KlasMotion.defaultEffects()),
            ) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.let { if (wanting) it.error else it.onSurfaceVariant },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Turned in the draw phase, so the fold's row is not recomposed on every frame of the turn.
            Icon(
                KlasIcons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
        Reveal(opened) {
            Column(
                Modifier.padding(top = Spacing.tight, bottom = Spacing.inline),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                content = content,
            )
        }
    }
}

internal const val FOLD_TAG = "booking_fold"

private const val HALF_TURN = 180f
