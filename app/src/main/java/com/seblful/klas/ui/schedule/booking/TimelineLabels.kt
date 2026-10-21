package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.timeText
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/*
 * The writing around a [BookingTimeline] bar, placed by where along the day it belongs.
 */

/**
 * The line above the bar: the tapped lesson in a bubble over it; else this lesson's start and end over its
 * edges; else how the bar is used.
 */
@Composable
internal fun Peek(timeline: DayTimeline, lesson: DayTimeline.Lesson?, chosen: Scheduling.Slot?, clashes: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = PeekHeight), contentAlignment = Alignment.BottomStart) {
        if (lesson == null && chosen != null) {
            val colors = MaterialTheme.colorScheme
            StartAndEnd(timeline, chosen, if (clashes) colors.error else colors.primary)
        } else if (lesson == null) {
            Text(
                stringResource(R.string.class_timeline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val from = timeline.fractionAt(maxOf(lesson.slot.start, timeline.start))
            val until = timeline.fractionAt(minOf(lesson.slot.end, timeline.end))
            CenteredOver(listOf((from + until) / 2)) {
                Row(
                    Modifier
                        .testTag(PEEK_TAG)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(lessonColors(lesson.assignee).first))
                    Text(
                        lessonText(lesson),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * [slot]'s start and end in numbers, the start ending at its left edge and the end beginning at its right
 * edge — kept inside the line, and never over each other.
 */
@Composable
internal fun StartAndEnd(timeline: DayTimeline, slot: Scheduling.Slot, color: Color) {
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    Layout(
        {
            listOf(slot.start, slot.end).forEach { edge ->
                Text(
                    edge.timeText(),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.testTag(EDGE_TAG),
                )
            }
        },
        Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val (start, end) = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val left = (timeline.fractionAt(maxOf(slot.start, timeline.start)) * width).roundToInt()
        val right = (timeline.fractionAt(minOf(slot.end, timeline.end)) * width).roundToInt()
        val startX = (left - gap - start.width).coerceIn(0, maxOf(0, width - start.width - end.width - gap))
        val endX = (right + gap).coerceIn(startX + start.width + gap, maxOf(0, width - end.width))
        layout(width, maxOf(start.height, end.height)) {
            start.place(startX, 0)
            end.place(endX, 0)
        }
    }
}

/** Hour labels under the bar, every [TICK_HOURS] hours, each centred on its hour. */
@Composable
internal fun Ticks(timeline: DayTimeline) {
    val hours = (0..timeline.minutes / MINUTES_PER_HOUR step TICK_HOURS).toList()
    CenteredOver(hours.map { timeline.fractionAt(timeline.start + it.hours) }) {
        hours.forEach { hour ->
            Text(
                (timeline.start + hour.hours).timeText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lays each child out centred over its fraction of the width, kept inside it. */
@Composable
internal fun CenteredOver(fractions: List<Float>, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { i, placeable ->
                val centre = (fractions.getOrElse(i) { 0f } * width).roundToInt()
                placeable.place((centre - placeable.width / 2).coerceIn(0, maxOf(0, width - placeable.width)), 0)
            }
        }
    }
}

/** How far along the timeline [at] is, from 0 at its start to 1 at its end. */
internal fun DayTimeline.fractionAt(at: Instant): Float = (at - start).inWholeMinutes.toFloat() / minutes

/** The time [fraction] of the way along the timeline — [fractionAt] turned round. */
internal fun DayTimeline.timeAt(fraction: Float): Instant = start + (fraction * minutes).roundToInt().minutes

/** «Мария · 15:30–16:30 · Очно»: who, when, where. */
@Composable
internal fun lessonText(lesson: DayTimeline.Lesson): String = bulletJoin(
    lesson.who,
    "${lesson.slot.start.timeText()}–${lesson.slot.end.timeText()}",
    classModeLabel(lesson.mode),
)

internal const val EDGE_TAG = "booking_timeline_edge"
internal const val PEEK_TAG = "booking_timeline_peek"

private const val MINUTES_PER_HOUR = 60
private const val TICK_HOURS = 3
private val PeekHeight = 32.dp
