package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.schedule.NowMarker
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatWeekRange
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.PageStepper
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@Composable
internal fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = date.formatDay(),
        previousDescription = stringResource(R.string.schedule_previous_day),
        nextDescription = stringResource(R.string.schedule_next_day),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
internal fun WeekNavigator(
    days: List<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatWeekRange(days.first(), days.last()),
        previousDescription = stringResource(R.string.schedule_previous_week),
        nextDescription = stringResource(R.string.schedule_next_week),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
internal fun MonthNavigator(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatMonth(month),
        previousDescription = stringResource(R.string.schedule_previous_month),
        nextDescription = stringResource(R.string.schedule_next_month),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
internal fun PageNavigator(
    title: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageStepper(
        previousDescription = previousDescription,
        nextDescription = nextDescription,
        onPrevious = onPrevious,
        onNext = onNext,
    ) {
        Text(
            title,
            // A heading, so a screen reader can jump between the page's title and its days.
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(
                minFontSize = with(LocalDensity.current) { NavigatorTitleMinSize.toSp() },
                maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
            ),
        )
        IconButton(onClick = onToday) {
            Icon(KlasIcons.Filled.Today, stringResource(R.string.schedule_today))
        }
    }
}

private val NavigatorTitleMinSize = 11.dp

@Composable
internal fun SelectedDayHeader(date: LocalDate, summary: DaySummary) {
    DaySectionHeader(date = date, isSelected = true, summary = summary)
}

@Composable
internal fun DaySectionHeader(date: LocalDate, isSelected: Boolean, summary: DaySummary) {
    Column(
        // One extra [Spacing.itemGap] above, so the gap that opens *before* a header is a section
        // break while the header still sits close to the day it names. The list's uniform `spacedBy`
        // cannot say that on its own.
        Modifier.fillMaxWidth().padding(top = Spacing.itemGap),
    ) {
        SectionTitle(
            date.formatDay(),
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        DaySummaryLine(summary)
    }
}

@Composable
internal fun DaySummaryLine(summary: DaySummary, modifier: Modifier = Modifier) {
    // Null is "nothing to say about this day" — the empty state below already says it at length, and a
    // «0 занятий · 0 ч» above that would be the same news twice in a colder voice.
    val text = daySummaryText(
        LocalContext.current.resources,
        summary,
        LocalCurrency.current,
    ) ?: return

    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.tight),
    )
}

@Composable
internal fun NowLine(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NowDotSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            stringResource(R.string.schedule_now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.inline),
        )
        HorizontalDivider(
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val NowDotSize = 6.dp
