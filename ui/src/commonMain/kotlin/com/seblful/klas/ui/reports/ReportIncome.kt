package com.seblful.klas.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatHours
import com.seblful.klas.format.formatMonthName
import com.seblful.klas.format.money
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringArrays
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringArrayResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.lessonKindFill
import com.seblful.klas.ui.common.lessonKindLabel
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.paymentMethodLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun IncomeCard(state: Reports.Report, showsReceived: Boolean = true) {
    // Earned is the default: income stays "earned" (D6), and received is the cash view beside it — not
    // offered for a subject, which no payment belongs to.
    var chosen by rememberSaveable { mutableStateOf(IncomeView.EARNED) }
    val view = if (showsReceived) chosen else IncomeView.EARNED
    HeroCard(
        label = stringResource(Strings.reports_total_income),
        value = (if (view == IncomeView.EARNED) state.totalIncome else state.received.total).text(),
    ) {
        if (showsReceived) {
            SegmentedChoice(
                options = IncomeView.entries,
                selected = view,
                onSelect = { chosen = it },
                modifier = Modifier.padding(vertical = Spacing.tight),
                // Filled with the card's ink: the default selected fill is the hero's own tint, and which of the
                // two the figure above is could not be told.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { stringResource(it.labelRes) }
        }
        when (view) {
            IncomeView.EARNED -> {
                if (!state.noIncome) TaughtLine(state.taught)
                ComparisonLine(state)
                Text(stringResource(Strings.reports_earned_hint), style = MaterialTheme.typography.bodySmall)
            }
            IncomeView.RECEIVED -> ReceivedDetail(state.received)
        }
    }
}

private enum class IncomeView(val labelRes: StringResource) {
    EARNED(Strings.reports_earned),
    RECEIVED(Strings.reports_received),
}

/** "121 lessons · 134 h · 42 ahead" — the period's teaching, on the hero rather than a card of its own. */
@Composable
private fun TaughtLine(taught: Taught.Totals) {
    val stat = lessonsStat(taught)
    Text(
        bulletJoin(
            listOfNotNull(
                pluralStringResource(Plurals.lesson_count, taught.lessons, taught.lessons),
                stringResource(Strings.hours_value, formatHours(taught.minutes)),
                stat.hint,
            ),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Money in — what was paid in the period, by how it was paid when more than one way was used. */
@Composable
private fun ReceivedDetail(received: Reports.Received) {
    if (received.showsMethods) {
        Text(
            bulletJoin(
                received.byMethod.map { line ->
                    val method = line.method?.let { paymentMethodLabel(it) }
                        ?: stringResource(Strings.payment_method_none)
                    "$method ${line.amount.text()}"
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(stringResource(Strings.reports_received_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ColumnScope.ComparisonLine(state: Reports.Report) {
    val change = state.change ?: return
    val previous = state.previousPeriod ?: return
    val previousName = periodTitle(previous)
    // While the period is still going the comparison is over the days it has had — and says so, or
    // «+12 % к периоду «2025»» would read as the whole of last year.
    val toDate = state.changeToDate
    val text = when (change) {
        Reports.Change.NoBaseline -> stringResource(
            if (toDate) Strings.reports_vs_previous_from_zero_to_date else Strings.reports_vs_previous_from_zero,
            previousName,
        )
        is Reports.Change.Percent -> stringResource(
            if (toDate) Strings.reports_vs_previous_to_date else Strings.reports_vs_previous,
            signedPercent(change.value),
            previousName,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
        // An arrow only where there is a direction: "the previous period earned nothing" has none,
        // and an upward arrow beside it would be asserting growth the app just declined to measure.
        if (change is Reports.Change.Percent && change.value != 0L) {
            Icon(
                // AutoMirrored: a trend arrow points *forward in time*, so it flips with the layout
                // direction. The non-mirrored pair is deprecated for exactly this reason.
                if (change.value > 0) {
                    KlasIcons.Filled.TrendingUp
                } else {
                    KlasIcons.Filled.TrendingDown
                },
                contentDescription = null,
                modifier = Modifier.size(TrendIconSize),
            )
            Spacer(Modifier.width(Spacing.tight))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun signedPercent(value: Long): String = when {
    value > 0 -> stringResource(Strings.reports_percent_signed_up, value)
    value < 0 -> stringResource(Strings.reports_percent_signed_down, -value)
    else -> stringResource(Strings.reports_percent_signed_same)
}

@Composable
internal fun TrendCard(trend: Reports.Trend) {
    val months = stringArrayResource(StringArrays.month_short_names)
    val isMonths = trend.bucket == Reports.Trend.Bucket.MONTHS
    ReportCard(stringResource(if (isMonths) Strings.reports_by_month else Strings.reports_by_year)) {
        IncomeTrendChart(trend) { bar -> if (isMonths) months[bar.monthIndex] else bar.key.toString() }
        // The accent bar explained, and named: the chart itself is not read aloud.
        trend.peak?.let { peak ->
            PeakLegend(
                if (isMonths) {
                    stringResource(Strings.reports_best_month, formatMonthName(peak.monthIndex))
                } else {
                    stringResource(Strings.reports_best_year, peak.key.toString())
                },
            )
        }
        KlasDivider()
        // `rows`, not `bars`: newest first and without the empty buckets, which are a gap in the chart
        // above and would only be a row of zeros here. Both halves of that are stated on [Reports.Trend].
        trend.rows.forEach { bar ->
            AmountRow(
                label = if (isMonths) formatMonthName(bar.monthIndex) else bar.key.toString(),
                amount = AmountText(bar.total.text()),
            )
        }
    }
}

/** A swatch of the peak bar's colour and what it marks. */
@Composable
private fun PeakLegend(text: String) {
    Row(
        Modifier.padding(top = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LegendSwatch)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val LegendSwatch = 10.dp

private val TrendIconSize = 16.dp

private val KindBarHeight = 12.dp

@Composable
private fun IncomeTrendChart(trend: Reports.Trend, labelOf: (Reports.Trend.Bar) -> String) {
    val peak = trend.peak ?: return
    val filled = MaterialTheme.colorScheme.primary
    val others = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    // Only the bars' tops are rounded: they stand on the axis.
    val barShape = MaterialTheme.shapes.extraSmall.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    Row(
        // Not read: twelve bare month names and no amounts. The rows under the chart are its text, with
        // the amounts, and a screen reader reads those instead.
        Modifier.fillMaxWidth().height(ChartHeight).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        trend.bars.forEach { bar ->
            // Against the peak already in hand, rather than finding it again for every bar.
            val share = animateBarShare(bar.total.minor.toFloat() / peak.total.minor)
            // The peak in the accent, the rest in the secondary role: both stand 3:1 off the track.
            val fill = if (bar.key == peak.key) filled else others
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The track and its fill on one node, with the outlines kept until the bar is resized or moves.
                Spacer(
                    Modifier.fillMaxWidth().weight(1f).drawWithCache {
                        val trackOutline = barShape.createOutline(size, layoutDirection, this)
                        val height = (size.height * share.value.coerceIn(0f, 1f)).roundToInt().toFloat()
                        val fillOutline = barShape.createOutline(Size(size.width, height), layoutDirection, this)
                        onDrawBehind {
                            drawOutline(trackOutline, track)
                            if (height > 0f) translate(top = size.height - height) { drawOutline(fillOutline, fill) }
                        }
                    },
                )
                // Shrinks rather than clipping: a twelfth of a phone at a large font scale cut «сен» to «се».
                // The peak's name in its bar's accent and bold, tying it to the legend under the chart.
                val isPeak = bar.key == peak.key
                Text(
                    labelOf(bar),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPeak) filled else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isPeak) FontWeight.Bold else null,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(LocalDensity.current) { AxisMinSize.toSp() },
                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                    ),
                    modifier = Modifier.padding(top = Spacing.tight),
                )
            }
        }
    }
}

@Composable
internal fun KindSplitCard(lines: List<Reports.KindLine>) {
    ReportCard(stringResource(Strings.reports_by_kind)) {
        Row(Modifier.fillMaxWidth().height(KindBarHeight).clip(CircleShape)) {
            lines.forEach { line ->
                Box(
                    Modifier
                        // Weighted by the money itself, so the bar *is* the split.
                        .weight(line.amount.minor.toFloat())
                        .fillMaxHeight()
                        .background(lessonKindFill(line.kind)),
                )
            }
        }
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(LegendSwatch).clip(CircleShape).background(lessonKindFill(line.kind)))
                Spacer(Modifier.width(Spacing.inline))
                Text(
                    lessonKindLabel(line.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    stringResource(Strings.reports_amount_share, line.amount.text(), line.percent),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

internal val BarGap = Spacing.tight

private val ChartHeight = 116.dp

// A floor in dp, so a large font scale still leaves the month names room to shrink.
private val AxisMinSize = 9.dp
