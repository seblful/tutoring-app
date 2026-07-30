package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.seblful.klas.R
import com.seblful.klas.domain.CalendarLayout
import com.seblful.klas.format.shortName
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.map
import com.seblful.klas.ui.common.valueOrNull
import java.time.DayOfWeek
import java.time.LocalDate

/** The tallest a cell needs: the day-number disc plus its tally row, six rows still fitting a phone. */
private val CellHeight = 46.dp

/** The disc hugs the day number only, leaving the tally row the cell's full width to print in. */
private val NumberDiscSize = 28.dp

/** Reserved for the tally row whether or not the day has classes — `labelSmall`'s line height. */
private val TallyRowHeight = 16.dp

/**
 * Two hairline distances inside a cell, deliberately off the app's `Spacing` scale: seven cells share
 * a phone's width, so these are measured against the glyphs either side of them rather than set to the
 * app's rhythm. 4 dp between three tally digits would cost a two-digit count its room.
 */
private val TallySpacing = 3.dp
private val DiscToTally = 1.dp

/**
 * The bar standing in for a day's counts before they have been read: about one `labelSmall` digit wide,
 * and faint enough to read as "not filled in yet" rather than as a mark of its own. Off the spacing scale
 * for [TallySpacing]'s reason — it is measured against the glyph it replaces.
 */
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
    Column(modifier.fillMaxWidth()) {
        WeekdayHeader(grid.weeks.first())
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        load = loads.map { it[date] },
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
    loads: Loadable<Map<LocalDate, CalendarLayout.DayLoad>>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        WeekdayHeader(days)
        Row(Modifier.fillMaxWidth()) {
            days.forEach { date ->
                DayCell(
                    date = date,
                    load = loads.map { it[date] },
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

/**
 * The Пн…Вс above the cells, **read off the week it labels** rather than assembled from a week start of
 * its own. The row and the grid under it are then the same seven days by construction, so a tutor whose
 * phone starts the week on Sunday cannot be shown Sunday's lessons under «Пн» — which is what a second
 * copy of "where does a week start" would eventually do.
 */
@Composable
private fun WeekdayHeader(week: List<LocalDate>) {
    Row(Modifier.fillMaxWidth()) {
        week.forEach { date ->
            Text(
                text = date.dayOfWeek.shortName(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = weekdayColor(date.dayOfWeek),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    load: Loadable<CalendarLayout.DayLoad?>,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Selection fills the disc, so the number inside it switches to the on-primary colour.
    val numberColor = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        inMonth -> scheme.onSurface
        // Padding days from the neighbouring months stay legible but clearly secondary.
        else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    // Spoken as "15, 2 занятия, 1 Запланировано, 1 Отменено" — the tallies carry that breakdown
    // visually through colour alone, which a screen reader cannot convey, so it must be said. A cell
    // whose counts have not arrived says only its date, which is all that is known about it.
    val cellDescription = load.valueOrNull()?.let { dayLoad ->
        val total = pluralStringResource(R.plurals.lessons_count, dayLoad.total, dayLoad.total)
        val breakdown = dayLoad.tallies.map { "${it.count} ${classStatusLabel(it.status)}" }
        (listOf(date.dayOfMonth.toString(), total) + breakdown).joinToString(", ")
    }

    // The whole cell is the touch target, not just the disc drawn inside it — but press feedback
    // bounded to that rectangle reads as a grey square behind the day, so the cell hands its
    // interactions to the round disc below and draws no indication of its own.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(CellHeight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { if (cellDescription != null) contentDescription = cellDescription },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DiscToTally),
        ) {
            Box(
                Modifier
                    .size(NumberDiscSize)
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.background(scheme.primary) else Modifier)
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.dp, scheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    // Last in the chain so the ripple lands *over* a selected day's fill, and
                    // tinted like the number so it stays visible on every disc state.
                    .indication(interactionSource, ripple(color = numberColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = numberColor,
                )
            }
            DayTallies(load)
        }
    }
}

/**
 * How many lessons the day holds, split into one number per status and tinted with that status's
 * colour, so a mixed day states its makeup instead of collapsing to a single dominant hue. Sitting
 * *below* the day-number disc rather than inside it is what keeps those colours readable on the
 * selected day and leaves room for a two-digit count. Always reserves its row's height so day
 * numbers stay on the same baseline whether or not the day has classes.
 *
 * **A day with no counts yet is not a day with no lessons**, and in a grid the difference is invisible
 * unless something is drawn: an empty cell is how this calendar says «свободно», so until the query
 * answers, one dim bar stands where the numbers will be. That bar is deliberately **not** a
 * `SkeletonBlock` — the pulse is an `rememberInfiniteTransition` each, and a month grid would start
 * thirty-five of them to animate something the size of a digit.
 */
@Composable
private fun DayTallies(load: Loadable<CalendarLayout.DayLoad?>) {
    if (load is Loadable.Loading) {
        Row(
            Modifier.fillMaxWidth().height(TallyRowHeight),
            horizontalArrangement = Arrangement.spacedBy(TallySpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = TallyPlaceholderWidth, height = TallyPlaceholderHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TallyPlaceholderAlpha)),
            )
        }
        return
    }
    Row(
        // `fillMaxWidth` bounds the row to its **cell**, which a wrap-content row is not: three
        // two-digit tallies at a large font scale measured wider than a seventh of a phone and drew
        // over the neighbouring day. Centred within the cell, so a one-tally day still sits under its
        // own number.
        Modifier.fillMaxWidth().height(TallyRowHeight),
        horizontalArrangement = Arrangement.spacedBy(TallySpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        load.valueOrNull()?.tallies?.forEach { tally ->
            Text(
                text = tally.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = classStatusColor(tally.status),
                maxLines = 1,
            )
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
