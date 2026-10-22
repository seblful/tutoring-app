package com.seblful.klas.ui.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.shortName
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.bleed
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.rememberPlaceholderShown
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.theme.KlasMotion
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private val CellHeight = 48.dp

/**
 * How far the grid reaches into the gutter either side: seven 48 dp days across a 360 dp phone, as on
 * Material's date picker, where the gutter alone left each a little short of a thumb.
 */
private val GridBleed = 4.dp

private val NumberDiscSize = 28.dp

private val TodayRingWidth = 1.dp

private const val DiscToLineHeight = 1.4f

private val TallyRowHeight = 16.dp

private val TallySpacing = Spacing.tight
// Disc, gap and tallies: 28 + 4 + 16, the 48 dp a cell is.
private val DiscToTally = Spacing.tight

private val TallyPlaceholderWidth = 10.dp
private val TallyPlaceholderHeight = 4.dp
private const val TallyPlaceholderAlpha = 0.18f

/**
 * A month page: weekday headers over rows of day cells, each marked with a per-status count of its
 * classes. Tapping a cell selects that day; the class list below shows it.
 */
@Composable
fun MonthCalendar(
    grid: CalendarLayout.MonthGrid,
    loads: Loadable<Map<LocalDate, CalendarLayout.DayLoad>>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One for the grid, not one per cell — see [DayMarks].
    val placeholderShown = rememberPlaceholderShown(waiting = loads is Loadable.Loading)
    Column(modifier.fillMaxWidth().bleed(GridBleed)) {
        WeekdayHeader(grid.weeks.first())
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        // Remembered, so a tap re-selecting a day leaves the other cells' load the same
                        // instance and they skip, instead of all 42 formatting their descriptions again.
                        load = remember(loads, date) { loads.map { it[date] } },
                        placeholderShown = placeholderShown,
                        inMonth = grid.isInMonth(date),
                        isSelected = date == selected,
                        isToday = date == today,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        StatusLegend(Modifier.fillMaxWidth().padding(top = Spacing.tight))
    }
}

/**
 * A single week as one row of day cells, above the week's agenda. Selecting a day here doesn't
 * filter the agenda — the whole week stays visible — it picks the day the add button books into.
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    loads: Loadable<Map<LocalDate, CalendarLayout.DayLoad>>,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholderShown = rememberPlaceholderShown(waiting = loads is Loadable.Loading)
    Column(modifier.fillMaxWidth().bleed(GridBleed)) {
        WeekdayHeader(days)
        Row(Modifier.fillMaxWidth()) {
            days.forEach { date ->
                DayCell(
                    date = date,
                    load = remember(loads, date) { loads.map { it[date] } },
                    placeholderShown = placeholderShown,
                    inMonth = true,
                    isSelected = date == selected,
                    isToday = date == today,
                    onClick = { onSelect(date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        StatusLegend(Modifier.fillMaxWidth().padding(top = Spacing.tight))
    }
}

@Composable
private fun WeekdayHeader(week: List<LocalDate>) {
    Row(Modifier.fillMaxWidth()) {
        week.forEach { date ->
            Text(
                text = date.dayOfWeek.shortName(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = weekdayColor(MaterialTheme.colorScheme, date.dayOfWeek),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    load: Loadable<CalendarLayout.DayLoad?>,
    placeholderShown: State<Boolean>,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val numberColor = dayNumberColor(scheme, isSelected = isSelected, isToday = isToday, inMonth = inMonth)
    // The disc fills and empties as the selection moves, as Material's date picker fades its days, rather than
    // swapping in a frame. Both are read in the draw: a tap would otherwise recompose two cells per frame.
    val selection = animateFloatAsState(if (isSelected) 1f else 0f, KlasMotion.fastEffects(), label = "day fill")
    val ink = animateColorAsState(numberColor, KlasMotion.fastEffects(), label = "day number")
    // Spoken as «Четверг, 15 октября, 2 занятия, В плане: 1, Отменено: 1» — the marks carry that
    // breakdown as shapes, which a screen reader cannot convey, so it must be said,
    // and the day is said in full rather than as a bare «15». A cell whose counts have not arrived says
    // only its date, which is all that is known about it. Which day is selected, and which is today, are
    // said as the cell's state — see the semantics below.
    val day = date.formatDay()
    val cellDescription = load.valueOrNull()?.let { dayLoad ->
        val total = pluralStringResource(R.plurals.lessons_count, dayLoad.total, dayLoad.total)
        val breakdown = dayLoad.tallies.map {
            stringResource(R.string.calendar_status_tally, classStatusLabel(it.status), it.count)
        }
        (listOf(day, total) + breakdown).joinToString(", ")
    } ?: day
    val todayLabel = stringResource(R.string.schedule_today)
    val selectedLabel = stringResource(R.string.calendar_selected)

    // The whole cell is the touch target, not just the disc drawn inside it — but press feedback
    // bounded to that rectangle reads as a grey square behind the day, so the cell hands its
    // interactions to the round disc below and draws no indication of its own.
    val interactionSource = remember { MutableInteractionSource() }
    // The disc grows with the number it rounds: 28 dp at the default font size, and a circle around a
    // larger digit rather than a 28 dp crop of it at a large one.
    val discSize = with(LocalDensity.current) {
        maxOf(NumberDiscSize, (MaterialTheme.typography.bodyMedium.lineHeight * DiscToLineHeight).toDp())
    }
    Box(
        modifier
            // At least, not exactly: a cell and its tally row sized in dp around text sized in sp cut the
            // tallies off, and then the numbers, once the phone's font was turned up.
            .heightIn(min = CellHeight)
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = cellDescription
                // Said in one: a state of its own replaces the «selected» a selectable would say.
                if (isToday) stateDescription = if (isSelected) "$todayLabel, $selectedLabel" else todayLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DiscToTally),
        ) {
            Box(
                Modifier
                    .size(discSize)
                    .clip(CircleShape)
                    .drawBehind {
                        // Today's ring lies under the fill, which covers it once today is selected.
                        if (isToday) {
                            val ring = TodayRingWidth.toPx()
                            drawCircle(scheme.primary, radius = (size.minDimension - ring) / 2, style = Stroke(ring))
                        }
                        drawRect(scheme.primary.copy(alpha = selection.value.coerceIn(0f, 1f)))
                    }
                    // Last in the chain so the ripple lands *over* a selected day's fill, and
                    // tinted like the number so it stays visible on every disc state.
                    .indication(interactionSource, ripple(color = numberColor)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = { ink.value },
                )
            }
            DayMarks(load, placeholderShown)
        }
    }
}

@Composable
private fun DayMarks(load: Loadable<CalendarLayout.DayLoad?>, placeholderShown: State<Boolean>) {
    if (load is Loadable.Loading) {
        val placeholderTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TallyPlaceholderAlpha)
        Row(
            Modifier.fillMaxWidth().heightIn(min = TallyRowHeight),
            horizontalArrangement = Arrangement.spacedBy(TallySpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = TallyPlaceholderWidth, height = TallyPlaceholderHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .drawBehind { if (placeholderShown.value) drawRect(placeholderTint) },
            )
        }
        return
    }
    Row(
        // `fillMaxWidth` bounds the row to its **cell**, which a wrap-content row is not: three
        // two-digit tallies at a large font scale measured wider than a seventh of a phone and drew
        // over the neighbouring day. Centred within the cell, so a one-tally day still sits under its
        // own number.
        Modifier.fillMaxWidth().heightIn(min = TallyRowHeight),
        horizontalArrangement = Arrangement.spacedBy(MarkGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val marks = load.valueOrNull()?.marks ?: return@Row
        marks.shown.forEach { StatusMark(it) }
        if (marks.more > 0) {
            Text(
                text = stringResource(R.string.calendar_more_marks, marks.more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Selection fills the disc, so the number inside it switches to the on-primary colour. */
internal fun dayNumberColor(scheme: ColorScheme, isSelected: Boolean, isToday: Boolean, inMonth: Boolean): Color =
    when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        inMonth -> scheme.onSurface
        // Padding days from the neighbouring months are secondary, yet tappable, so they keep text contrast.
        else -> scheme.onSurfaceVariant
    }

/** Weekdays in ink as on Material's date picker; the weekend recedes to the variant, which still reads as text. */
internal fun weekdayColor(scheme: ColorScheme, day: DayOfWeek): Color =
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) scheme.onSurfaceVariant else scheme.onSurface
