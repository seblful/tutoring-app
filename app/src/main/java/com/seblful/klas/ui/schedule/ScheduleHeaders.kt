package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatWeekRange
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.PageStepper
import com.seblful.klas.ui.common.ScreenTip
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.pageByArrow
import com.seblful.klas.ui.designsystem.component.CoachMark
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SliceHeader
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@Composable
internal fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    PageNavigator(
        title = date.formatDay(),
        previousDescription = stringResource(R.string.schedule_previous_day),
        nextDescription = stringResource(R.string.schedule_next_day),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
        selected = selected,
        onPick = onPick,
    )
}

@Composable
internal fun WeekNavigator(
    days: List<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    PageNavigator(
        title = formatWeekRange(days.first(), days.last()),
        previousDescription = stringResource(R.string.schedule_previous_week),
        nextDescription = stringResource(R.string.schedule_next_week),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
        selected = selected,
        onPick = onPick,
    )
}

@Composable
internal fun MonthNavigator(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    PageNavigator(
        title = formatMonth(month),
        previousDescription = stringResource(R.string.schedule_previous_month),
        nextDescription = stringResource(R.string.schedule_next_month),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
        selected = selected,
        onPick = onPick,
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
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val direction = LocalLayoutDirection.current
    PageStepper(
        previousDescription = previousDescription,
        nextDescription = nextDescription,
        onPrevious = onPrevious,
        onNext = onNext,
    ) {
        Text(
            title,
            // A heading, so a screen reader can jump between the page's title and its days. A tap picks any
            // date: paging a day or a month at a time was the only way to reach one weeks away.
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(MaterialTheme.shapes.small)
                .onKeyEvent { pageByArrow(it, direction, onPrevious, onNext) }
                .clickable(
                    onClickLabel = stringResource(R.string.schedule_pick_date),
                    role = Role.Button,
                ) { picking = true }
                .padding(horizontal = Spacing.inline, vertical = Spacing.tight)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(
                minFontSize = with(LocalDensity.current) { NavigatorTitleMinSize.toSp() },
                maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
            ),
        )
        IconButton(onClick = onToday) {
            Icon(KlasIcons.Outlined.Today, stringResource(R.string.schedule_today))
        }
    }
    if (picking) {
        DatePickerModal(
            initial = selected,
            onConfirm = {
                picking = false
                onPick(it)
            },
            onDismiss = { picking = false },
        )
    }
}

private val NavigatorTitleMinSize = 11.dp

@Composable
internal fun SelectedDayHeader(
    date: LocalDate,
    summary: DaySummary,
    position: SlicePosition,
    action: SectionAction? = null,
) {
    DaySectionHeader(date = date, isSelected = true, summary = summary, position = position, action = action)
}

/**
 * A day of the Week or Month list: the top slice of its section card (R1) — the date, what the day comes to as
 * its count, and one action — with the day's lessons as the slices under it. The date is tinted when it is the
 * selected day, and that is said in words by the strip or grid it was picked from.
 */
@Composable
internal fun DaySectionHeader(
    date: LocalDate,
    isSelected: Boolean,
    summary: DaySummary,
    position: SlicePosition,
    action: SectionAction? = null,
) {
    SliceHeader(
        title = date.formatDay(),
        position = position,
        // The gap before a day is the section break; the day's own rows follow it without one.
        modifier = Modifier.padding(top = Spacing.itemGap),
        summary = daySummaryText(LocalContext.current.resources, summary, LocalCurrency.current),
        action = action,
        titleColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
}

@Composable
internal fun DaySummaryLine(summary: DaySummary, modifier: Modifier = Modifier) {
    // Null is "nothing to say about this day" — the empty state below already says it at length, and a
    // «0 занятий · 0 ч» above that would be the same news twice in a colder voice. The line keeps its height
    // all the same: the Day view's header is pinned, and paging onto an empty day shrank it and jumped the list.
    val text = daySummaryText(
        LocalContext.current.resources,
        summary,
        LocalCurrency.current,
    )

    val style = MaterialTheme.typography.bodySmall
    Text(
        text.orEmpty(),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = Spacing.tight)
            // An empty text is not a line tall.
            .heightIn(min = with(LocalDensity.current) { style.lineHeight.toDp() })
            .then(if (text == null) Modifier.clearAndSetSemantics { } else Modifier),
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
        KlasDivider(
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val NowDotSize = 6.dp

/** The page title under the schedule's date tip while it carries it — see [Tip.SCHEDULE_DATE]. */
@Composable
internal fun DateTip(tip: ScreenTip, content: @Composable () -> Unit) {
    if (tip.tip != Tip.SCHEDULE_DATE) {
        content()
        return
    }
    CoachMark(stringResource(R.string.tip_schedule_date), visible = true, onDismiss = tip.dismiss, content = content)
}
