package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.sectionSurface
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate

@Composable
internal fun ScheduleEmptyState(
    reason: Loadable<ScheduleEmpty>,
    whole: ScheduleEmptySpan,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
    minHeight: Dp,
) {
    val modifier = Modifier.heightIn(min = minHeight)
    // Which empty this is waits on whether anything can be booked — see [ScheduleViewModel.emptyReason].
    val known = when (reason) {
        Loadable.Loading -> {
            LessonsSkeleton()
            return
        }
        is Loadable.Ready -> reason.value
    }
    when (known) {
        ScheduleEmpty.FILTERED -> EmptyState(
            title = stringResource(Strings.schedule_filter_empty),
            detail = stringResource(Strings.schedule_filter_empty_detail),
            icon = KlasIcons.Outlined.EventBusy,
            modifier = modifier,
        )

        ScheduleEmpty.QUIET -> EmptyState(
            title = stringResource(
                when (whole) {
                    ScheduleEmptySpan.DAY -> Strings.schedule_empty
                    ScheduleEmptySpan.WEEK -> Strings.schedule_week_empty
                },
            ),
            detail = stringResource(Strings.schedule_empty_detail),
            icon = KlasIcons.Outlined.EventBusy,
            actionLabel = stringResource(Strings.schedule_add_class),
            onAction = onAddClass,
            modifier = modifier,
        )

        ScheduleEmpty.NEEDS_STUDENT -> EmptyState(
            title = stringResource(Strings.schedule_empty_no_setup),
            detail = stringResource(Strings.schedule_empty_no_setup_detail),
            icon = KlasIcons.Outlined.PersonAdd,
            actionLabel = stringResource(Strings.schedule_add_student),
            onAction = onAddStudent,
            modifier = modifier,
        )
    }
}

internal fun LazyListScope.dayList(
    view: ScheduleViewMode,
    sliced: Boolean = false,
    state: Loadable<ScheduleContent.Day>,
    emptyReason: Loadable<ScheduleEmpty>,
    nowMark: Int?,
    phases: Map<Long, LessonPhase>,
    timeWidth: Dp,
    onRowClick: (ScheduleRow) -> Unit,
    onComplete: (ScheduleRow) -> Unit,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
) {
    val day = when (state) {
        Loadable.Loading -> {
            item(key = "loading", contentType = "loading") { LessonsSkeleton() }
            return
        }
        is Loadable.Ready -> state.value
    }
    val rows = day.rows
    if (rows.isEmpty()) {
        item(key = "empty", contentType = "empty") {
            ScheduleEmptyState(
                reason = emptyReason,
                whole = ScheduleEmptySpan.DAY,
                onAddClass = onAddClass,
                onAddStudent = onAddStudent,
                minHeight = DayEmptyMinHeight,
            )
        }
    } else {
        // A row at a time rather than `items(rows)`, so the mark can be its own item *between* two
        // lessons. It has to be one: `nowMark == rows.size` means the whole day is behind us, and there
        // is no card left to draw it above — and an item with two roots would take [animateInPlace] off
        // the card, which is what keeps a re-sorted list from teleporting.
        rows.forEachIndexed { index, row ->
            if (index == nowMark) item(key = nowKey(view, day.date), contentType = "now") { DayNowLine(sliced) }
            item(key = lessonKey(view, row, day.date), contentType = "lesson") {
                SwipeToCompleteCard(
                    row = row,
                    phase = phases.of(row),
                    timeWidth = timeWidth,
                    onClick = { onRowClick(row) },
                    onComplete = { onComplete(row) },
                    slice = if (sliced) lastOrMiddle(index, rows.size) else null,
                )
            }
        }
        if (nowMark == rows.size) item(key = nowKey(view, day.date), contentType = "now") { DayNowLine(sliced) }
    }
}

/** The now-line between a day's lessons — on the day's section surface when they are its slices. */
@Composable
private fun LazyItemScope.DayNowLine(sliced: Boolean) {
    val surface = if (sliced) Modifier.background(MaterialTheme.colorScheme.sectionSurface) else Modifier
    NowLine(animateInPlace().then(surface))
}

/** The chosen day of the Month view: the top slice of its section, with «Open day» to the Day view (R1). */
@Composable
internal fun ChosenDayHeader(dayContent: Loadable<ScheduleContent.Day>, selected: LocalDate, onOpenDay: () -> Unit) {
    val day = dayContent.valueOrNull()
    SelectedDayHeader(
        date = day?.date ?: selected,
        summary = day?.summary ?: DaySummary.NONE,
        position = if (day?.rows.isNullOrEmpty()) SlicePosition.ONLY else SlicePosition.FIRST,
        action = SectionAction(stringResource(Strings.schedule_open_day), onClick = onOpenDay),
    )
}

/** A day's lesson [index] of [count] as a slice under its header: the last one rounds the card off. */
internal fun lastOrMiddle(index: Int, count: Int): SlicePosition =
    if (index == count - 1) SlicePosition.LAST else SlicePosition.MIDDLE

internal fun lessonKey(view: ScheduleViewMode, row: ScheduleRow, day: LocalDate? = null): String =
    "$view/${day ?: ""}/${row.klass.id}"

private fun nowKey(view: ScheduleViewMode, day: LocalDate): String = "$view/$day/now"

@Composable
internal fun LessonsSkeleton() {
    ListSkeleton(rows = 3, contentPadding = PaddingValues(0.dp))
}

internal val WeekEmptyMinHeight = 200.dp

private val DayEmptyMinHeight = 260.dp
