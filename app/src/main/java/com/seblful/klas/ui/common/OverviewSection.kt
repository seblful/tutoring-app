package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The parts a student's and a group's card are built of: headline cards, then sections showing the latest
 * few of a list with the way to the rest.
 */

/**
 * One of a card's sections: a heading with how many it holds, the latest few, and — when there are more than
 * it shows — the way to the rest.
 *
 * @param count 0 says no count, for a section that shows all it holds.
 * @param onSeeAll null when every row is already shown.
 */
@Composable
fun OverviewSection(
    title: String,
    count: Int,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .padding(start = Spacing.gutter, end = Spacing.tight, top = Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(title)
            if (count > 0) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.inline),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) {
                    Text(stringResource(R.string.section_see_all))
                    Icon(
                        KlasIcons.Filled.ChevronRight,
                        contentDescription = null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
        content()
        Spacer(Modifier.height(Spacing.inline))
    }
}

/** What a section says with nothing in it — a line, not an illustrated empty state, inside a card. */
@Composable
fun SectionNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
    )
}

/** Under a section's add row, the «+» lines up with the rows' leading edge. */
val AddRowInSection = Modifier.padding(start = Spacing.tight)

/** Rows inside a section stand on the section, not on a fill of their own. */
@Composable
fun sectionRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

/** The floor a headline card's one-line figure shrinks to before it gives up a character. */
val MinHeadlineSize = 18.dp

/** A headline card's padding: its trailing button sits closer to the edge than the text does. */
val HeadlineCardInset = Modifier
    .fillMaxWidth()
    .padding(start = Spacing.gutter, end = Spacing.itemGap, top = Spacing.gutter, bottom = Spacing.gutter)
