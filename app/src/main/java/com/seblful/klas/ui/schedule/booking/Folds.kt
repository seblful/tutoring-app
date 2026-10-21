package com.seblful.klas.ui.schedule.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The booking form's folded rows — Length, Repeat, Terms: one line saying what each is set to, opened in
 * place by a tap on it.
 */

/**
 * A card of the form folded to its title and what it is set to — see [Fold]. Folded until the tutor opens
 * it: what it holds is usually already right.
 */
@Composable
internal fun FoldedSection(
    title: String,
    summary: String,
    wanting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
            Fold(label = { SectionTitle(title) }, summary = summary, wanting = wanting, content = content)
        }
    }
}

/**
 * A line with [label] and what it is set to — [summary], red when [wanting] — that a tap opens to [content]
 * and closes again. It is pressed as the app's rows are, a rounded tint rather than a grey band
 * ([outsetRowPress]); it opens by growing downwards, the summary fading as the fields it sums up appear,
 * and its chevron turns to say which way the next tap goes.
 */
@Composable
internal fun Fold(
    label: @Composable () -> Unit,
    summary: String,
    wanting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (opened) HALF_TURN else 0f, label = "fold chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(FOLD_TAG)
                .outsetRowPress { source ->
                    Modifier.clickable(source, indication = null, role = Role.Button) { opened = !opened }
                }
                .semantics {
                    if (opened) collapse { opened = false; true } else expand { opened = true; true }
                }
                .heightIn(min = MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        ) {
            Box(Modifier.weight(1f)) { label() }
            AnimatedVisibility(!opened, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.let { if (wanting) it.error else it.onSurfaceVariant },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(KlasIcons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(
            opened,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
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
