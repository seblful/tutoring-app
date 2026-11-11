@file:Suppress("ForbiddenImport") // The stat strip is a design-system surface.

package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.Spacing

/**
 * One number a card leads with — a debt, a lesson count, an amount earned.
 *
 * @param value the number itself, already formatted (money carries its currency, hours do not).
 * @param label what it counts, e.g. «Часов».
 * @param hint an optional second line qualifying the value, e.g. «впереди 3» under a lesson count —
 * for the part of the picture the headline number deliberately leaves out.
 * @param color an override for the value's colour, e.g. red for a debt. Null uses the inherited one.
 */
data class Stat(
    val label: String,
    val value: String,
    val hint: String? = null,
    val color: Color? = null,
)

/**
 * The stat row at the top of a student's or a group's card: two to four [Stat]s sharing one surface,
 * each taking an equal share of the width.
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    // A section, not an item: one rung above the cards in the list beneath it.
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.sectionSurface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.itemGap),
            // Top, so every value sits on one line even when only one tile has a hint under it.
            verticalAlignment = Alignment.Top,
        ) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    VerticalDivider(
                        Modifier.height(StatDividerHeight).align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                StatTile(stat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = TileInsetX),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ValueToLabel),
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleMedium,
            color = stat.color ?: LocalContentColor.current,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // Only reachable when even the smallest step overflows (a very large font scale).
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                // In dp, like the bar title's and the tab labels' floors: the font scale is already in the
                // size it starts from, and scaling the floor too left a wide amount nothing to shrink into.
                minFontSize = with(LocalDensity.current) { MinValueSize.toSp() },
                maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
            ),
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        stat.hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Off the 4 dp grid on purpose: at 320 dp, 8 dp leaves «1000000,00 BYN» no room. Pinned by `StatRowAutoSizeTest`. */
private val TileInsetX = 6.dp
private val ValueToLabel = 0.dp

private val MinValueSize = 11.dp

private val StatDividerHeight = 36.dp
