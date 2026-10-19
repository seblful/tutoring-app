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
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
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

/**
 * A lesson card that can be marked **completed** with one swipe.
 *
 * Marking a lesson done is the highest-frequency action in the product and cost two taps and a sheet,
 * every time, forever. A gesture on the row makes it one — and the row is exactly where the tutor is
 * already looking, having just taught it.
 *
 * Three deliberate limits:
 * - **Only where completing is legal.** A lesson already completed, or one whose members are in mixed
 *   states, gets a plain card: [ScheduleRow.canSwipeComplete] decides, from the same rule the sheet's
 *   actions are built from, so the gesture can never do something the menu would refuse.
 * - **It is taken back by sliding home.** The phone ticks at the mark and the lesson is marked only on
 *   the lift, so a swipe begun by accident — or aimed at the row below — costs a thumb moving back and
 *   nothing else. [SwipeToConfirmBox] owns that whole contract.
 * - **One direction, and it never sticks.** The row springs back rather than staying dismissed —
 *   nothing is being removed, and the status badge flipping to «Проведено» is the confirmation. A box
 *   left in its dismissed state would be a row hidden behind its own background until recomposed.
 */
@Composable
internal fun LazyItemScope.SwipeToCompleteCard(
    row: ScheduleRow,
    running: Boolean,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!row.canSwipeComplete) {
        ScheduleCard(row, running, onClick, animateInPlace(modifier))
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
            onClick,
            Modifier.swipeAction(stringResource(R.string.class_mark_completed), onComplete),
        )
    }
}

@Composable
private fun ScheduleCard(
    row: ScheduleRow,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the card recedes is the row's own answer — see [ScheduleRow.recedes]: a day with
    // four lessons and three cancellations must not look like a full day.
    val cancelled = row.recedes
    ContentCard(onClick = onClick, modifier = modifier, dimmed = cancelled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.klass.start.timeText(),
                style = MaterialTheme.typography.titleMedium,
                color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
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

/**
 * A lesson's second line, on its card and atop its sheet: the status, then what is taught and where.
 *
 * **The status leads**, as it does on every other lesson row in the app (the student's and the group's
 * cards). At the trailing edge it took ~90dp off the name's line, so on a 360dp phone «Артём Савиц…» lost
 * the surname — the one thing on the card that says whose lesson this is — to a pill saying something the
 * colour of the time already half-says. The facts after it go through [bulletJoin], which drops a blank
 * one: a lesson whose subject has no name read «· Очно». The card keeps it to one line; the sheet
 * passes [maxLines] through so a long subject name is read whole there.
 */
@Composable
internal fun StatusLine(row: ScheduleRow, running: Boolean, maxLines: Int = 1) {
    Row(
        Modifier.padding(top = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionStatusBadge(row, running)
        Spacer(Modifier.width(Spacing.inline))
        Text(
            bulletJoin(row.subjectName, classModeLabel(row.klass.mode)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The lesson's status — one of the three, whatever its members have been corrected to individually.
 * Which of them missed it is the lesson's own roster to say, not this badge's.
 *
 * **It pops when it changes**, because it is the answer to a swipe the tutor has just let go of — the row
 * springs home under their thumb and the badge is where the outcome lands, a moment later and 200dp away
 * (see [popsOnChange], which is silent for a badge that merely scrolled into view). It pops for a status
 * corrected from the sheet too: the same fact changed under them, by a different route.
 */
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
