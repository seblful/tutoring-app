package com.seblful.klas.ui.schedule.booking

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.avatarColors
import com.seblful.klas.ui.common.rememberStepTickFeedback
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasMotion
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The booking's day as one bar: each lesson already in it in its student's colour, and this one, [chosen],
 * in indigo with its start and end written over its edges. A tap on a lesson says above the bar who it is
 * for, when and where, and a second tap puts that away; a tap on free time starts the lesson there. A slide
 * anywhere on the bar moves the lesson from where it is — at half the finger's speed, so each five-minute
 * step is a few millimetres and a tick under the finger, and hopping over the day's lessons rather than
 * stopping at them. It is drawn as it moves and booked when the finger lifts. Before a start is chosen,
 * [suggestion] is drawn dashed and booked by a tap. [onStart] is given an exact start, already free;
 * [length] is how long the lesson is. Once it is known who the lesson is for — [mine] — their other
 * lessons that day stand out, outlined, among everyone else's. A chosen lesson also has buttons under
 * the bar that move it five minutes either way — the precise way, and the one a screen reader uses.
 */
@Composable
internal fun BookingTimeline(
    timeline: DayTimeline,
    chosen: Scheduling.Slot?,
    length: Int,
    clashes: Boolean,
    onStart: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    suggestion: Scheduling.Slot? = null,
    mine: Assignee? = null,
) {
    // Which lesson was tapped — the screen's own business, and forgotten with the day it was on.
    var peeked by remember(timeline.day) { mutableStateOf<DayTimeline.Lesson?>(null) }
    // A slide under way, and where it has the lesson starting until the finger lifts.
    var slide by remember(timeline.day) { mutableStateOf<Slide?>(null) }
    var sliding by remember(timeline.day) { mutableStateOf<Instant?>(null) }
    // A slide moves [sliding] every five minutes of it, so it is read only where it is drawn — the times over
    // the bar and the lesson's block — and here only whether there is one, which flips twice a slide.
    val drawn = { sliding?.let { Scheduling.Slot(it, length) } ?: chosen }
    val lifted by remember(timeline.day) { derivedStateOf { sliding != null } }
    val tick = rememberStepTickFeedback()
    val shown = peeked?.takeIf { it in timeline.booked }
    val red = !lifted && clashes
    val nudge = { by: Duration -> chosen?.let { timeline.shifted(it.start, by, length) }?.let(onStart) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Peek(timeline, shown, drawn, red)
        Bar(
            timeline = timeline,
            chosen = drawn,
            clashes = red,
            lifted = lifted,
            suggestion = suggestion,
            peeked = shown,
            mine = mine,
            // A second tap on the same lesson puts its bubble away again; a tap on this one leaves it be.
            onTap = { at ->
                val lesson = timeline.lessonAt(at)
                peeked = lesson?.takeIf { it != shown }
                val onChosen = chosen != null && at >= chosen.start && at < chosen.end
                if (lesson == null && !onChosen) timeline.place(at, length)?.let(onStart)
            },
            onSlideFrom = { at ->
                peeked = null
                slide = (chosen?.start ?: timeline.nearestFree(at, length))?.let(::Slide)
                sliding = slide?.origin
            },
            onSlideBy = { fraction ->
                slide?.let { current ->
                    current.travelled += fraction * timeline.minutes * SLIDE_SPEED
                    val next = timeline.nearestFree(current.origin + current.travelled.roundToInt().minutes, length)
                    if (next != null && next != sliding) {
                        tick()
                        sliding = next
                    }
                }
            },
            onSlid = {
                sliding?.let(onStart)
                sliding = null
                slide = null
            },
            onStart = onStart,
        )
        Ticks(timeline)
        if (chosen != null) Nudges(onEarlier = { nudge(-STEP) }, onLater = { nudge(STEP) })
    }
}

/** A slide in progress: where the lesson started, and how many minutes along the finger has taken it. */
private class Slide(val origin: Instant) {
    var travelled = 0f
}

@Composable
private fun Bar(
    timeline: DayTimeline,
    chosen: () -> Scheduling.Slot?,
    clashes: Boolean,
    lifted: Boolean,
    suggestion: Scheduling.Slot?,
    peeked: DayTimeline.Lesson?,
    mine: Assignee?,
    onTap: (Instant) -> Unit,
    onSlideFrom: (Instant) -> Unit,
    onSlideBy: (Float) -> Unit,
    onSlid: () -> Unit,
    onStart: (Instant) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val described = timeline.booked.map { lessonText(it) }.joinToString("; ")
    BoxWithConstraints(
        Modifier
            .testTag(TIMELINE_TAG)
            .fillMaxWidth()
            .height(BarHeight)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh)
            .drawBehind { hourLines(timeline, colors.outlineVariant.copy(alpha = HOUR_LINE_ALPHA)) }
            .semantics { contentDescription = described }
            .barTouch(
                rememberUpdatedState(timeline),
                rememberUpdatedState(onTap),
                rememberUpdatedState(onSlideFrom),
                rememberUpdatedState(onSlideBy),
                rememberUpdatedState(onSlid),
            ),
    ) {
        val width = maxWidth
        timeline.booked.forEach { lesson ->
            BookedBlock(timeline, lesson, width, peeked = lesson == peeked, mine = lesson.assignee == mine)
        }
        ChosenOrSuggestion(timeline, chosen, width, clashes, lifted, suggestion, onStart)
    }
}

/** The lesson being booked, or before it has a start the one offered — read here, as it slides, not in [Bar]. */
@Composable
private fun ChosenOrSuggestion(
    timeline: DayTimeline,
    chosen: () -> Scheduling.Slot?,
    width: Dp,
    clashes: Boolean,
    lifted: Boolean,
    suggestion: Scheduling.Slot?,
    onStart: (Instant) -> Unit,
) {
    val slot = chosen()
    if (slot != null) {
        ChosenBlock(timeline, slot, width, clashes, lifted)
    } else if (suggestion != null) {
        Suggestion(timeline, suggestion, width, onStart)
    }
}

/**
 * A lesson already on the day: a block in its colour, with the first letter of who it is for if it fits —
 * outlined if it is [mine] — for whoever this lesson is for.
 */
@Composable
private fun BookedBlock(
    timeline: DayTimeline,
    lesson: DayTimeline.Lesson,
    width: Dp,
    peeked: Boolean,
    mine: Boolean,
) {
    val (fill, letter) = lessonColors(lesson.assignee)
    val shape = MaterialTheme.shapes.extraSmall
    val outline = when {
        peeked -> MaterialTheme.colorScheme.onSurface
        mine -> letter
        else -> null
    }
    Segment(
        timeline,
        lesson.slot,
        width,
        Modifier
            .padding(vertical = Spacing.inline, horizontal = BlockSeparation)
            .fillMaxHeight()
            .then(if (mine) Modifier.testTag(MINE_TAG) else Modifier)
            .clip(shape)
            .background(fill)
            .then(if (outline != null) Modifier.border(OutlineWidth, outline, shape) else Modifier),
    ) { span ->
        if (span >= LetterMinWidth) {
            Text(
                lesson.who.take(1),
                style = MaterialTheme.typography.labelMedium,
                color = letter,
                maxLines = 1,
            )
        }
    }
}

/**
 * This lesson: indigo — red on a clash — with a grip to say it slides, raised while it does: from Material's
 * resting level to its dragged one, and a little larger, in the draw phase alone.
 */
@Composable
private fun ChosenBlock(timeline: DayTimeline, slot: Scheduling.Slot, width: Dp, clashes: Boolean, lifted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val grip = if (clashes) colors.onError else colors.onPrimary
    val shape = MaterialTheme.shapes.extraSmall
    val lift = animateFloatAsState(if (lifted) 1f else 0f, KlasMotion.fastSpatial(), label = "lift")
    Segment(
        timeline,
        slot,
        width,
        Modifier
            .padding(vertical = Spacing.tight)
            .fillMaxHeight()
            .graphicsLayer {
                val raised = lift.value
                shadowElevation = lerp(RestingElevation, DraggedElevation, raised).toPx()
                this.shape = shape
                scaleX = 1f + LiftedGrowth * raised
                scaleY = 1f + LiftedGrowth * raised
            }
            .background(if (clashes) colors.error else colors.primary, shape),
    ) { span ->
        if (span >= GripMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                repeat(2) { Box(Modifier.size(width = GripWidth, height = GripHeight).background(grip, CircleShape)) }
            }
        }
    }
}

/** Where the lesson could go before it has a start — dashed, with a plus, and booked there by a tap. */
@Composable
private fun Suggestion(timeline: DayTimeline, suggestion: Scheduling.Slot, width: Dp, onStart: (Instant) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.extraSmall
    val label = stringResource(R.string.class_timeline_suggestion, suggestion.start.timeText())
    Segment(
        timeline,
        suggestion,
        width,
        Modifier
            .padding(vertical = Spacing.tight)
            .fillMaxHeight()
            .testTag(SUGGESTION_TAG)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val dashed = Stroke(
                    width = OutlineWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
                )
                onDrawBehind {
                    drawOutline(outline, color = primary.copy(alpha = SUGGESTION_FILL))
                    drawOutline(outline, color = primary, style = dashed)
                }
            }
            // Named, not only labelled: the plus drawn inside is hidden, and a narrow frame drops it.
            .semantics { contentDescription = label }
            .clickable(onClickLabel = label, role = Role.Button) { onStart(suggestion.start) },
    ) { span ->
        if (span >= GripMinWidth) Icon(KlasIcons.Filled.Add, contentDescription = null, tint = primary)
    }
}

/** [slot]'s stretch of a bar [width] wide — clipped where it runs off either end — told how wide it is. */
@Composable
private fun Segment(
    timeline: DayTimeline,
    slot: Scheduling.Slot,
    width: Dp,
    modifier: Modifier,
    content: @Composable (span: Dp) -> Unit = {},
) {
    val from = maxOf(slot.start, timeline.start)
    val until = minOf(slot.end, timeline.end)
    if (until <= from) return
    val span = width * (timeline.fractionAt(until) - timeline.fractionAt(from))
    Box(
        Modifier
            .offset(x = width * timeline.fractionAt(from))
            .width(span)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) { content(span) }
}

/** A faint line on every whole hour inside the bar, so where a finger is reads as a time. */
private fun DrawScope.hourLines(timeline: DayTimeline, color: Color) {
    for (hour in 1 until timeline.minutes / MINUTES_PER_HOUR) {
        val x = size.width * timeline.fractionAt(timeline.start + hour.hours)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

/**
 * Taps on the bar, read as the time under the finger, and slides, read as how far along the bar the finger
 * has gone; through [State]s, so a gesture sees today's values.
 */
private fun Modifier.barTouch(
    timeline: State<DayTimeline>,
    onTap: State<(Instant) -> Unit>,
    onSlideFrom: State<(Instant) -> Unit>,
    onSlideBy: State<(Float) -> Unit>,
    onSlid: State<() -> Unit>,
): Modifier = pointerInput(Unit) {
    detectTapGestures { offset -> onTap.value(timeline.value.timeAt(offset.x / size.width)) }
}.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { offset -> onSlideFrom.value(timeline.value.timeAt(offset.x / size.width)) },
        onDragEnd = { onSlid.value() },
        onDragCancel = { onSlid.value() },
    ) { change, amount ->
        change.consume()
        onSlideBy.value(amount / size.width)
    }
}

/**
 * A lesson's colours, fill then letter: its student's avatar colours; a group's, the colours every group
 * avatar has.
 */
@Composable
internal fun lessonColors(assignee: Assignee): Pair<Color, Color> = when (assignee) {
    is Assignee.Student -> avatarColors(assignee.studentId).let { (disc, letter) ->
        disc.compositeOver(MaterialTheme.colorScheme.surface) to letter
    }
    is Assignee.Group -> MaterialTheme.colorScheme.let { it.secondaryContainer to it.onSecondaryContainer }
}

internal const val TIMELINE_TAG = "booking_timeline"
internal const val SUGGESTION_TAG = "booking_timeline_suggestion"
internal const val MINE_TAG = "booking_timeline_mine"

private val STEP = DayTimeline.SNAP_MINUTES.minutes

/** How far a slide moves the lesson for the finger's travel: half, so a five-minute step is easy to land. */
private const val SLIDE_SPEED = 0.5f
private const val SUGGESTION_FILL = 0.08f
private const val HOUR_LINE_ALPHA = 0.6f
private const val MINUTES_PER_HOUR = 60
private val BarHeight = 48.dp

/** A hairline of bar between neighbouring blocks, so two lessons back to back read as two. */
private val BlockSeparation = 1.dp
private val OutlineWidth = 2.dp
private val DashOn = 5.dp
private val DashOff = 3.dp
private val GripWidth = 2.dp
private val GripHeight = 14.dp
private val RestingElevation = 1.dp
private val DraggedElevation = 6.dp
private const val LiftedGrowth = 0.08f
private val LetterMinWidth = 16.dp
private val GripMinWidth = 20.dp
