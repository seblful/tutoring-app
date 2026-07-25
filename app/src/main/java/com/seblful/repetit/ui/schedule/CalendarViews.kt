package com.seblful.repetit.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seblful.repetit.R
import com.seblful.repetit.domain.CalendarLayout
import com.seblful.repetit.format.shortName
import com.seblful.repetit.ui.common.classStatusColor
import java.time.DayOfWeek
import java.time.LocalDate

/** The tallest a cell needs: a day number plus its marker dots, six rows still fitting a phone. */
private val CellHeight = 44.dp

/** Dots stop at three — past that the count stops being readable and "busy" is the message. */
private const val MaxDots = 3

/**
 * A month page: weekday headers over rows of day cells, each marked with a dot per class (capped
 * at [MaxDots]). Tapping a cell selects that day; the class list below shows it.
 */
@Composable
fun MonthCalendar(
    grid: CalendarLayout.MonthGrid,
    loads: Map<LocalDate, CalendarLayout.DayLoad>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        WeekdayHeader()
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        load = loads[date],
                        inMonth = grid.isInMonth(date),
                        isSelected = date == selected,
                        isToday = date == today,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A single week as one row of day cells, above the week's agenda. Selecting a day here doesn't
 * filter the agenda — the whole week stays visible — it picks the day the add button books into.
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    loads: Map<LocalDate, CalendarLayout.DayLoad>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        WeekdayHeader()
        Row(Modifier.fillMaxWidth()) {
            days.forEach { date ->
                DayCell(
                    date = date,
                    load = loads[date],
                    inMonth = true,
                    isSelected = date == selected,
                    isToday = date == today,
                    onClick = { onSelect(date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        CalendarLayout.weekdays().forEach { day ->
            Text(
                text = day.shortName(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = weekdayColor(day),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    load: CalendarLayout.DayLoad?,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Selection is a filled disc, so everything inside it switches to the on-primary colour.
    val numberColor = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        inMonth -> scheme.onSurface
        // Padding days from the neighbouring months stay legible but clearly secondary.
        else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    // Spoken as "15, 2 занятия" — the dots carry that count visually, so it must be spoken too.
    val cellDescription = load?.let {
        "${date.dayOfMonth}, ${pluralStringResource(R.plurals.schedule_day_classes, it.total, it.total)}"
    }

    // The whole cell is the touch target, not just the disc drawn inside it.
    Box(
        modifier
            .height(CellHeight)
            .clickable(onClick = onClick)
            .semantics { if (cellDescription != null) contentDescription = cellDescription },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(CellHeight - 4.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(scheme.primary) else Modifier)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.dp, scheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
            )
            DayDots(
                load = load,
                color = if (isSelected) scheme.onPrimary else load?.let { classStatusColor(it.dominant) },
            )
        }
    }
}

/**
 * One dot per class, up to [MaxDots]. Always reserves its row's height so day numbers stay on the
 * same baseline whether or not the day has classes.
 */
@Composable
private fun DayDots(load: CalendarLayout.DayLoad?, color: Color?) {
    Row(
        Modifier.height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (load != null && color != null) {
            repeat(minOf(load.total, MaxDots)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** Weekends are muted so the working week reads first. */
@Composable
private fun weekdayColor(day: DayOfWeek): Color =
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
