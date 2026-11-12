package com.seblful.klas.ui.reports

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.AmountMeaning
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.theme.KlasMotion
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val ProgressBarHeight = 6.dp

@Composable
internal fun RankedLine(line: Reports.Line, onClick: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(BarGap)) {
        // Ink, like every other amount on the screen that is not a debt. Colour on this screen
        // means something — the hero's fill, a debt's red — and primary here meant nothing the
        // primary bar under it was not already saying.
        AmountRow(line.label, AmountText(line.amount.text()), onClick = onClick)
        // The fraction and the single-line rule are the report's own — see [Reports.Line.share].
        line.share?.let { share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankedBar(share, Modifier.weight(1f))
                // A percentage only where the rows genuinely partition the income.
                line.percent?.let {
                    Spacer(Modifier.width(Spacing.inline))
                    Text(
                        stringResource(Strings.reports_percent, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Drawn on one node: it is in every ranked row, and a section can run to hundreds of them. */
@Composable
fun RankedBar(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val fill = MaterialTheme.colorScheme.primary
    val shown = animateBarShare(fraction)
    Spacer(
        modifier.height(ProgressBarHeight).drawBehind {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            val filled = Size((size.width * shown.value.coerceIn(0f, 1f)).roundToInt().toFloat(), size.height)
            drawRoundRect(fill, size = filled, cornerRadius = CornerRadius(filled.minDimension / 2))
        },
    )
}

/**
 * A bar's share of its track as drawn: a new period's grows or shrinks out of the last one's rather than
 * replacing it, its spring clamped at the track's ends by whoever draws it. Read it in the draw.
 */
@Composable
internal fun animateBarShare(share: Float): State<Float> =
    animateFloatAsState(share, KlasMotion.defaultSpatial(), label = "bar share")

internal fun LazyListScope.debtsSection(state: Reports.Report, onStudentClick: (Long) -> Unit) {
    // «Текущий баланс, за всё время» belongs to the title: it says why this card ignores the period. As
    // the card's first row it sat a full item gap below the heading and read as a caption to the total.
    item(key = "debt/heading", contentType = CARD_HEADING) {
        CardSlice(first = true) {
            CardHeading(stringResource(Strings.reports_debts), stringResource(Strings.reports_debts_all_time))
        }
    }
    if (state.debts.isEmpty()) {
        item(key = "debt/none", contentType = "debt-none") {
            CardSlice(last = true, gapAbove = Spacing.itemGap) {
                Text(
                    stringResource(Strings.reports_no_debts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    item(key = "debt/total", contentType = "debt-total") {
        CardSlice(gapAbove = Spacing.itemGap) {
            AmountRow(
                label = stringResource(Strings.reports_total_outstanding),
                amount = AmountText(state.totalOutstanding.text()),
                bold = true,
                // In the column the debts below it are in, which their chevron pulls inwards.
                alignWithLinks = true,
            )
        }
    }
    // Every row opens the person who owes: reading a name here and then going to find them by hand would
    // be the longest journey in the app, between two screens that are one tap apart.
    //
    // No gap between the rows: each is already a full touch target, and an item gap on top of that would
    // space the debts twice as far apart as any other rows on the screen.
    itemsIndexed(state.debts, key = { _, debt -> "debt/${debt.id}" }, contentType = { _, _ -> "debt" }) { index, debt ->
        CardSlice(last = index == state.debts.lastIndex, gapAbove = if (index == 0) Spacing.itemGap else 0.dp) {
            AmountRow(
                label = debt.label,
                amount = AmountText(debt.amount.text(), AmountMeaning.Debt),
                onClick = debt.studentId?.let { id -> { onStudentClick(id) } },
            )
        }
    }
}
