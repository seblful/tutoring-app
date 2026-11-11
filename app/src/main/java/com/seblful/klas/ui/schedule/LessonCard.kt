package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.ColumnTime
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.SwipeReveal
import com.seblful.klas.ui.common.SwipeToConfirmBox
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.lessonBadgeColor
import com.seblful.klas.ui.common.lessonBadgeLabel
import com.seblful.klas.ui.common.popsOnChange
import com.seblful.klas.ui.common.sectionSurface
import com.seblful.klas.ui.common.swipeAction
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.CoachMark
import com.seblful.klas.ui.designsystem.component.CoachMarkSide
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.designsystem.component.SurfaceRow
import com.seblful.klas.ui.designsystem.component.shapeOf
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasColors

@Composable
internal fun LazyItemScope.SwipeToCompleteCard(
    row: ScheduleRow,
    phase: LessonPhase,
    timeWidth: Dp,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    slice: SlicePosition? = null,
) {
    val picking = LocalLessonPicking.current
    val lessonTip = LocalLessonTip.current
    val tip = lessonTip.on(row)
    val reader = lessonTip.reader
    val tap = if (picking.active) ({ picking.toggle(row) }) else onClick
    // However it is marked — swiped, or through the action a screen reader offers — the swipe has been learned.
    val complete = {
        lessonTip.screen.done(Tip.LESSON_SWIPE)
        onComplete()
    }
    if (!row.canSwipeComplete || picking.active) {
        LessonCoachMark(tip, reader, lessonTip.screen.dismiss, animateInPlace(modifier)) { anchored ->
            ScheduleCard(row, phase, timeWidth, tap, anchored, picking, slice)
        }
        return
    }
    LessonCoachMark(tip, reader, lessonTip.screen.dismiss, animateInPlace(modifier)) { anchored ->
        val peek = tip == Tip.LESSON_SWIPE && !reader
        SwipeCard(row, phase, timeWidth, tap, complete, anchored, slice, picking, peek)
    }
}

/**
 * [content] under the schedule's tip when this card carries one — its modifier then goes on the bubble's anchor,
 * which is the list item's root — and as it is otherwise.
 */
@Composable
private fun LessonCoachMark(
    tip: Tip?,
    reader: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (tip == null) {
        content(modifier)
        return
    }
    val text = when (tip) {
        Tip.LESSON_SWIPE -> if (reader) Strings.tip_lesson_swipe_reader else Strings.tip_lesson_swipe
        else -> Strings.tip_lesson_pick
    }
    CoachMark(
        stringResource(text),
        visible = true,
        onDismiss = onDismiss,
        modifier = modifier,
        side = CoachMarkSide.Above,
    ) { content(Modifier) }
}

@Composable
private fun SwipeCard(
    row: ScheduleRow,
    phase: LessonPhase,
    timeWidth: Dp,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
    slice: SlicePosition?,
    picking: LessonPicking,
    peek: Boolean,
) {
    SwipeToConfirmBox(
        onConfirm = onComplete,
        modifier = modifier,
        peek = peek,
        // **Green, the container tone of the badge this gesture produces.** It was `tertiaryContainer`, which
        // in this palette is a pale cyan — so the swipe promised «Проведено» in a blue, the colour a lesson
        // *still to come* is written in, and then handed back a badge of another colour entirely. Now the
        // panel and the badge are one green and one word: what the reveal shows is what the row will say
        // (see `classStatusColor`).
        reveal = { armed ->
            SwipeReveal(
                icon = KlasIcons.Filled.CheckCircle,
                label = classStatusLabel(LessonStatus.COMPLETED),
                container = klasColors.successContainer,
                content = klasColors.onSuccessContainer,
                armed = armed,
                shape = slice?.shapeOf(MaterialTheme.shapes.medium) ?: MaterialTheme.shapes.medium,
                horizontalPadding = SheetGutter,
            )
        },
    ) {
        ScheduleCard(
            row,
            phase,
            timeWidth,
            onClick,
            Modifier.swipeAction(stringResource(Strings.class_mark_completed), onComplete),
            picking,
            slice,
        )
    }
}

@Composable
private fun ScheduleCard(
    row: ScheduleRow,
    phase: LessonPhase,
    timeWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picking: LessonPicking = LessonPicking.NONE,
    slice: SlicePosition? = null,
) {
    // Whether the card recedes is the row's own answer — see [ScheduleRow.recedes]: a day with
    // four lessons and three cancellations must not look like a full day.
    val cancelled = row.recedes
    LessonSurface(row, onClick, modifier, picking, slice) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ink, not the accent: the time is read, not tapped. A fixed-width column, so every card's
            // name starts on the same edge whatever the hour. The end under the start, quieter: back-to-back
            // lessons and the gaps between them read off the column without a time grid.
            Column {
                ColumnTime(
                    row.klass.start.timeText(),
                    style = MaterialTheme.typography.titleMedium,
                    width = timeWidth,
                    color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    // Struck through, so the hour reads as one that was given back.
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
                ColumnTime(
                    row.klass.end.timeText(),
                    style = MaterialTheme.typography.bodySmall,
                    width = timeWidth,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
            }
            // [Spacing.itemGap] rather than [Spacing.leading]: the time and the body are two peers
            // in the row, and every dp between them is width the lesson's own name does not get —
            // that name being the one thing on the card that can run long.
            Spacer(Modifier.width(Spacing.itemGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Only the group lesson is marked — the one that differs, and the one whose card goes on
                    // to list its members. An icon on every row said "person" beside every name (R1).
                    if (row.isGroup) {
                        LessonKindIcon(LessonKind.GROUP, Modifier.padding(end = Spacing.inline))
                    }
                    // **One line.** A name long enough to wrap — «Группа Биология» — would break
                    // across two, and because the time on the left is centred against the whole card
                    // it would then sit beside the *second* line with a hole above it. Rows would also
                    // stop being a uniform height, which is what a scannable list is for.
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Under the icon, not hung under the name: the status pill starts the card's second line on
                // the same edge as its first.
                StatusLine(row, phase)
                if (row.isGroup) {
                    Text(
                        // The roster is the least important line on the card and the most likely to
                        // run long, so it is the one allowed to be cut.
                        row.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A card of its own in the Day view; a row of its day's section in Week and Month (R1). */
@Composable
private fun LessonSurface(
    row: ScheduleRow,
    onClick: () -> Unit,
    modifier: Modifier,
    picking: LessonPicking,
    slice: SlicePosition?,
    content: @Composable () -> Unit,
) {
    val pickLabel = stringResource(Strings.batch_select)
    if (slice == null) {
        ContentCard(
            onClick = onClick,
            modifier = modifier,
            dimmed = row.recedes,
            selected = picking.isPicked(row),
            onLongClick = { picking.toggle(row) },
            onLongClickLabel = pickLabel,
        ) { content() }
    } else {
        SurfaceRow(
            position = slice,
            onClick = onClick,
            modifier = modifier,
            selected = picking.isPicked(row),
            container = MaterialTheme.colorScheme.sectionSurface,
            dimmed = row.recedes,
            onLongClick = { picking.toggle(row) },
            onLongClickLabel = pickLabel,
        ) { content() }
    }
}

@Composable
internal fun StatusLine(row: ScheduleRow, phase: LessonPhase, maxLines: Int = 1, rate: String? = null) {
    BadgeLine(badge = { SessionStatusBadge(row, phase) }, Modifier.padding(top = Spacing.tight)) {
        Text(
            bulletJoin(listOfNotNull(row.subjectName, classModeLabel(row.klass.mode), rate)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionStatusBadge(row: ScheduleRow, phase: LessonPhase) {
    val status = row.session.status
    StatusBadge(
        text = lessonBadgeLabel(status, phase),
        color = lessonBadgeColor(status, phase),
        // Keyed on **what the badge says**, not on the status alone: a lesson beginning changes the word
        // without changing the status, and a pill that changed silently at four o'clock would be the one
        // moment in the app where a state change is not announced.
        modifier = Modifier.popsOnChange(status to phase),
    )
}
