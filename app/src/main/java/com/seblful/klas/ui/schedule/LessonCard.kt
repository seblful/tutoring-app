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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
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
import com.seblful.klas.ui.common.swipeAction
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasColors

@Composable
internal fun LazyItemScope.SwipeToCompleteCard(
    row: ScheduleRow,
    running: Boolean,
    timeWidth: Dp,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!row.canSwipeComplete) {
        ScheduleCard(row, running, timeWidth, onClick, animateInPlace(modifier))
        return
    }
    SwipeToConfirmBox(
        onConfirm = onComplete,
        modifier = animateInPlace(modifier),
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
                shape = MaterialTheme.shapes.medium,
                horizontalPadding = SheetGutter,
            )
        },
    ) {
        ScheduleCard(
            row,
            running,
            timeWidth,
            onClick,
            Modifier.swipeAction(stringResource(R.string.class_mark_completed), onComplete),
        )
    }
}

@Composable
private fun ScheduleCard(
    row: ScheduleRow,
    running: Boolean,
    timeWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the card recedes is the row's own answer — see [ScheduleRow.recedes]: a day with
    // four lessons and three cancellations must not look like a full day.
    val cancelled = row.recedes
    ContentCard(onClick = onClick, modifier = modifier, dimmed = cancelled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ink, not the accent: the time is read, not tapped. A fixed-width column, so every card's
            // name starts on the same edge whatever the hour.
            ColumnTime(
                row.klass.start.timeText(),
                style = MaterialTheme.typography.titleMedium,
                width = timeWidth,
                color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                // Struck through, so the hour reads as one that was given back.
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            )
            // [Spacing.itemGap] rather than [Spacing.leading]: the time and the body are two peers
            // in the row, and every dp between them is width the lesson's own name does not get —
            // that name being the one thing on the card that can run long.
            Spacer(Modifier.width(Spacing.itemGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Both kinds are marked, not just the group ones: an icon that appears only
                    // sometimes reads as "this lesson has something extra", which leaves an individual
                    // lesson looking like a lesson with a missing icon rather than an individual one.
                    LessonKindIcon(LessonKind.of(row.klass), Modifier.padding(end = Spacing.inline))
                    // **One line.** A name long enough to wrap — «Группа Биология» — used to break
                    // across two, and because the time on the left is centred against the whole card
                    // it then sat beside the *second* line with a hole above it. Rows also stopped
                    // being a uniform height, which is what a scannable list is for.
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Under the icon, not hung under the name: the status pill starts the card's second line on
                // the same edge as its first.
                StatusLine(row, running)
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

@Composable
internal fun StatusLine(row: ScheduleRow, running: Boolean, maxLines: Int = 1) {
    BadgeLine(badge = { SessionStatusBadge(row, running) }, Modifier.padding(top = Spacing.tight)) {
        Text(
            bulletJoin(row.subjectName, classModeLabel(row.klass.mode)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionStatusBadge(row: ScheduleRow, running: Boolean) {
    val status = row.session.status
    StatusBadge(
        text = lessonBadgeLabel(status, running),
        color = lessonBadgeColor(status, running),
        // Keyed on **what the badge says**, not on the status alone: a lesson beginning changes the word
        // without changing the status, and a pill that changed silently at four o'clock would be the one
        // moment in the app where a state change is not announced.
        modifier = Modifier.popsOnChange(status to running),
    )
}
