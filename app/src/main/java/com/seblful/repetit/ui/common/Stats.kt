package com.seblful.repetit.ui.common

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One number a card leads with — a debt, a lesson count, an amount earned.
 *
 * @param value the number itself, already formatted (money carries its currency, hours do not).
 * @param label what it counts, e.g. «Часов».
 * @param hint an optional second line qualifying the value, e.g. «впереди 3» under a lesson count —
 *   for the part of the picture the headline number deliberately leaves out.
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
 *
 * Values shrink to fit rather than wrapping or being cut (the same rule and mechanism as the bottom
 * bar's labels): a tile is a fixed fraction of the screen, and «1 250,50 BYN» next to «Заработано» is
 * genuinely wider than a phone's quarter — but a truncated amount is worse than a small one, and a
 * wrapped one would shove the row's tiles to different heights.
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    VerticalDivider(
                        Modifier.height(36.dp),
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
        modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
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
                minFontSize = MinValueFontSize,
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
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val MinValueFontSize = 11.sp
