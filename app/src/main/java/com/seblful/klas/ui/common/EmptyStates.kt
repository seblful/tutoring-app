package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.theme.KlasIcons

/*
 * What a list says when it has nothing in it — and, where there is one obvious thing to do, that action as a
 * real button.
 *
 * An empty list has to carry the way out of itself. The exception is a list empty because *nothing matched a
 * search*, which says so and offers nothing: the way out is the field the tutor is already looking at.
 */

/**
 * What a list says when it has nothing in it.
 *
 * @param title the one-line statement of what is missing.
 * @param detail an optional second line, for when *why* it is empty is not obvious from the title.
 * @param icon a glyph for the thing that is missing. Decorative — the title carries the meaning.
 * @param actionLabel with [onAction], the primary way out of the empty state.
 * @param compact for an empty state **inside** something — a detail card's tab — rather than one owning
 * the screen. Smaller icon, no `fillMaxSize`, a quieter line, an outlined action.
 *
 * Tabs use this too rather than a bare grey `Text`, which would have nowhere to put an action — the part
 * of this component that matters.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            // A compact one is a block inside a list, so it takes the height it needs. A full one owns
            // the screen and centres in it.
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .padding(if (compact) Spacing.sectionGap else EmptyStateInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) CompactIconSize else EmptyStateIconSize),
                // Faint: it is scenery for the sentence, not a thing to look at.
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Text(
            text = title,
            // A tab's empty state sits under a heading that already says what the tab is, so its own
            // line is a statement rather than a headline.
            style = if (compact) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (compact) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            // Outlined inside a tab: a tab's way out is not competing with the card's own pencil.
            //
            // Tonal on a whole screen, **not filled**: every screen with one of these also has its add
            // button, and a filled indigo pill in the middle of the page outshouted that button — two
            // primaries for one action, and the louder of them the one that disappears as soon as the
            // list has a row. The add button is the screen's primary; this points at the same door.
            if (compact) {
                KlasButton(onClick = onAction, kind = ButtonKind.Outlined, withIcon = true) {
                    Icon(KlasIcons.Filled.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text(actionLabel, Modifier.padding(start = Spacing.inline))
                }
            } else {
                KlasButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = Spacing.tight),
                    kind = ButtonKind.Tonal,
                    withIcon = true,
                ) {
                    Icon(KlasIcons.Filled.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text(actionLabel, Modifier.padding(start = Spacing.inline))
                }
            }
        }
    }
}
/**
 * A list empty because **nothing matched the search** — the one empty state with no action: the way out
 * is the field the tutor is already typing in. "Nothing matched «зщ»" is a typo to fix; offering to
 * create a student, a group or a subject there would answer a question nobody asked.
 */
@Composable
fun NoSearchResults(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.search_no_results),
        detail = stringResource(R.string.search_no_results_detail),
        icon = KlasIcons.Outlined.SearchOff,
        modifier = modifier,
    )
}
/** The one way to add to a tab's list, sitting at its top where a section header's action used to. */
@Composable
fun AddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** «+» for a row that adds; a row that changes what is listed («Change the roster») says so with its own. */
    icon: ImageVector = KlasIcons.Filled.Add,
) {
    KlasButton(
        onClick = onClick,
        // No inset of its own: the button's content padding already puts the «+» on the rows' text edge
        // below it. An extra step here drew it a column to the right of every row it adds to.
        modifier = modifier.heightIn(min = MinTouchTarget),
        kind = ButtonKind.Text,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, Modifier.padding(start = Spacing.inline))
    }
}
private val EmptyStateIconSize = 56.dp

private val CompactIconSize = 32.dp

private val EmptyStateInset = 32.dp
