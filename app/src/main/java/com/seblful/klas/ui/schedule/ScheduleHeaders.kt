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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
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
import java.time.LocalDate
import java.time.YearMonth

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

/**
 * ‹ title › with a "jump to today" button — the day stepper, the week's and the month's ([PageStepper]).
 *
 * The title takes the space the arrows leave and shrinks to fit before it ellipsizes: «Понедельник, 21
 * сентября» lost its month to an ellipsis on a 6.1" phone at the default font size. Its floor is in **dp**,
 * like the bar title's and the tab labels': the font scale is already in the size it starts from, and an sp
 * floor rose with it and left the day nothing to shrink into at 1.3×.
 */
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
            Icon(Icons.Filled.Today, stringResource(R.string.schedule_today))
        }
    }
}

/**
 * The smallest a page title is drawn, on screen, before it ellipsizes — `labelSmall`'s size at the default
 * scale, the app's smallest text. What it takes for «Понедельник, 30 сентября» to fit a 320dp phone beside
 * three icon buttons; pinned by `PageTitleFitTest`.
 */
private val NavigatorTitleMinSize = 11.dp

/**
 * Names the day the list below belongs to, since the calendar's title is the month. The week agenda's
 * heading for the selected day, exactly — the same day named two ways on two views was two styles for
 * one fact.
 */
@Composable
internal fun SelectedDayHeader(date: LocalDate, summary: DaySummary) {
    DaySectionHeader(date = date, isSelected = true, summary = summary)
}

/**
 * Divides one day's classes from the next in the week agenda. The selected day is tinted so the
 * strip's selection stays traceable in the list below it.
 */
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

/**
 * What the day comes to: «4 занятия · 5 ч · 240 BYN».
 *
 * The screen listed a day's lessons and left the tutor to add them up — including the money, which is
 * what the app is for. It goes wherever a day is *named*: under the stepper in the day view, under the
 * selected-day heading in the month view, and on each heading of the week agenda. One rule, no
 * exceptions to remember.
 *
 * The arithmetic is [DaySummary]'s and the **wording is [daySummaryText]'s** — shared with the morning
 * digest, which summarises the same day into the notification tray. Two copies of this sentence could
 * add one day up two ways, and both numbers would look right. A **cancelled** lesson is named apart
 * rather than folded in: it counts towards nothing, and a day of four lessons and one cancellation must
 * not read as five.
 */
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

/**
 * The hairline that says **where now is** in today's list.
 *
 * A day is a column of times with nothing marking which of them have been — so the next lesson, the one
 * row the tutor can still act on, has to be found by reading the clock and then reading the list. The
 * mark is drawn only on today ([NowMarker] decides, not this) and it moves as the clock does.
 *
 * A dot and a rule in the brand colour, with the word: a bare tinted line among lesson cards would read
 * as a divider, and the label is also the only part of this a screen reader can announce.
 */
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

/** The mark's dot: a measurement of a drawn shape, not a gap — see `Spacing`. */
private val NowDotSize = 6.dp
