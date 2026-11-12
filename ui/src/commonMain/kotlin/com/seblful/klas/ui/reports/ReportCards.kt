package com.seblful.klas.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.sectionSurface
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.Amount
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.coroutines.flow.first

internal const val CARD_HEADING = "heading"

@Composable
internal fun ReportCard(
    title: String,
    /** A line qualifying the title, set tight under it — part of the heading, not the first row. */
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            CardHeading(title, subtitle)
            content()
        }
    }
}

@Composable
internal fun CardHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        SectionTitle(title)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One list item's share of a card drawn across several: the [first] rounds the card's top and stands
 * [Spacing.itemGap] below the item before it, the [last] rounds its bottom, and [gapAbove] is the room
 * the card's own column would have left above this row.
 */
@Composable
internal fun CardSlice(
    first: Boolean = false,
    last: Boolean = false,
    gapAbove: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = MaterialTheme.shapes.medium
    val square = CornerSize(0.dp)
    val shape = card.copy(
        topStart = if (first) card.topStart else square,
        topEnd = if (first) card.topEnd else square,
        bottomStart = if (last) card.bottomStart else square,
        bottomEnd = if (last) card.bottomEnd else square,
    )
    // The surface [SectionCard] is, so a sliced card reads as the cards beside it — painted rather than a
    // `Surface`, which added a node, a clip layer and a semantics group to every row of the report. Nothing
    // inside needs the clip: a debt's press is clipped to its own shape, inset from the card's edge.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (first) Spacing.itemGap else 0.dp)
                .background(MaterialTheme.colorScheme.sectionSurface, shape)
                .padding(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = if (first) Spacing.gutter else gapAbove,
                    bottom = if (last) Spacing.gutter else 0.dp,
                ),
            content = content,
        )
    }
}

/**
 * A label on the left, its amount on the right — the shape every number on this screen is read in.
 *
 * @param onClick makes the row a target. Only the debts pass one: those name a person to go and talk
 * to, while «Июль · 12 400» names a month, which is not somewhere to be taken. A row that leads
 * somewhere gets a chevron and a full-height touch target so it says so before it is tapped.
 */
@Composable
internal fun AmountRow(
    label: String,
    amount: AmountText,
    bold: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Leaves the room a linked row's chevron and inset take, so a total lines up over the rows it sums. */
    alignWithLinks: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                when {
                    // The ripple needs room either side of the text, and taking it from inside the card
                    // put the debts' names a step to the right of every other label on the screen. It is
                    // taken from the card's padding instead, so the text stays on the shared edge.
                    onClick != null ->
                        Modifier
                            .outsetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) }
                            .heightIn(min = MinTouchTarget)
                    else -> Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.itemGap))
        Amount(
            amount,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
        )
        if (onClick != null) {
            Icon(
                KlasIcons.Filled.ChevronRight,
                contentDescription = stringResource(Strings.reports_open_student),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChevronSize),
            )
        } else if (alignWithLinks) {
            Spacer(Modifier.width(ChevronSize))
        }
    }
}

private val ChevronSize = 18.dp
