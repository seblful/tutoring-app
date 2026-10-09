package com.seblful.klas.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.format.toLocalTime
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.SheetHandle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.rememberToggleFeedback
import com.seblful.klas.ui.common.timeText
import java.time.LocalDate

/**
 * What can be done to the lesson that was tapped: change its status, tick off the homework attached to it,
 * edit it, move it, delete it.
 *
 * **Status comes first and is a control, not a list of verbs.** It is the most common reason the sheet
 * is open by a wide margin, and it used to be one or two ordinary rows in the middle of a flat menu,
 * indistinguishable from «Перенести» — so the sheet answered "what can I do" without answering "what
 * is this lesson now".
 *
 * Every action is the **whole lesson's**, a group's included. The sheet deliberately does not list
 * the members as a roster: who a lesson covers is picked while booking it, on the form, and a menu that
 * changes a lesson's status is not where a roster belongs. The one place it names one is the homework, when
 * a member's own is attached to a group's lesson — see [SheetHomework.who].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassActionsSheet(
    row: ScheduleRow,
    running: Boolean,
    sheetState: androidx.compose.material3.SheetState,
    homework: List<SheetHomework>,
    onHomeworkDone: (Homework, Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatus: (LessonStatus) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val klass = row.klass
    // No `dragHandle`: Material wraps whatever is passed there in a clickable, tooltipped box of its own,
    // so even a plain pill lit grey when held. The pill is the sheet's first row instead — see
    // [SheetHandle]; the whole sheet drags, not only the handle.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(bottom = SheetGutter)) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            // Header: which lesson these actions apply to.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LessonKindIcon(LessonKind.of(klass), Modifier.padding(end = Spacing.inline))
                Column(Modifier.weight(1f)) {
                    Text(
                        bulletJoin(klass.start.timeText(), row.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // The lesson card's own second line — status first — so the sheet opens on the
                    // row that was tapped, in the same order, rather than a rearrangement of it.
                    StatusLine(row, running, maxLines = Int.MAX_VALUE)
                }
            }

            // The lesson's state, as a state: every status it can be in, with the current one shown
            // selected. Offering only the transitions (as a list of actions did) meant the sheet never
            // said what the lesson *is* — the tutor had to infer it from which verbs were absent.
            StatusSelector(row = row, onStatus = onStatus)

            // What was set for this lesson, **with its ticks on the sheet**: the start of a lesson is when
            // the tutor asks for it, and this is the screen they are on.
            if (homework.isNotEmpty()) AttachedHomework(homework, onHomeworkDone)

            HorizontalDivider(Modifier.padding(vertical = Spacing.inline))

            // Editing details (student/subject/price) is allowed in any status; saving a completed
            // class re-bills it so its recognized income tracks the edit (see ClassRules.rebill).
            // A group lesson edits as a whole — every member's class moves and re-bills together.
            SheetAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit)
            // Move (reschedule) is offered for any status: moving a lesson to the future re-plans it,
            // moving it to the past just corrects its recorded time. A group lesson moves as one.
            SheetAction(stringResource(R.string.class_move), Icons.Filled.EditCalendar, onClick = onMove)
            // Delete is offered for any status. Deleting a completed class removes its recognized
            // income (the confirm dialog warns about this); the repository deletes the calendar
            // event and refreshes reminders regardless of status.
            HorizontalDivider(Modifier.padding(vertical = Spacing.inline))
            SheetAction(
                stringResource(R.string.action_delete),
                Icons.Filled.Delete,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

/**
 * The homework attached to this lesson (`HomeworkRules.attachedToLesson`), each with a tick — the same
 * «done» the tab's swipe gives, kept on screen once given so a wrong tap is taken back where it was made.
 * Capped at a handful of lines each: the sheet is a set of actions, and the tab has the whole text.
 */
@Composable
private fun AttachedHomework(items: List<SheetHomework>, onDone: (Homework, Boolean) -> Unit) {
    // A stored fact changing under the thumb, like the roster's and the settings' ticks — felt the same way.
    val toggleFeedback = rememberToggleFeedback()
    Column(Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline)) {
        SectionTitle(stringResource(R.string.homework_label))
        items.forEach { item ->
            val done = item.homework.isDone
            Row(
                Modifier
                    .fillMaxWidth()
                    .outsetRowPress { source ->
                        Modifier.toggleable(
                            value = done,
                            interactionSource = source,
                            indication = null,
                            role = Role.Checkbox,
                        ) { toggleFeedback(it); onDone(item.homework, it) }
                    }
                    // A thumb's height, like every other row the app ticks — it was the checkbox's own 24 dp.
                    .heightIn(min = MinTouchTarget)
                    .padding(vertical = Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No handler of its own: the row is the target, so the whole line ticks it.
                Checkbox(checked = done, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = Spacing.inline)) {
                    item.who?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    Text(
                        item.homework.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = SheetHomeworkLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** How much of one piece of homework the actions sheet shows before the tab is the way to read the rest. */
private const val SheetHomeworkLines = 4

/**
 * The lesson's status as a segmented control.
 *
 * Shows all three, with the lesson's own selected — so the sheet states the fact as well as offering
 * the change. There is always one selected, [ClassSession.status] being one of the three even for a
 * lesson whose members were corrected apart; the segments it *enables* are still the union of what
 * those members can each move to ([SessionRules.sessionTransitions]).
 *
 * **The current status is enabled and does nothing.** [ClassRules.availableTransitions] excludes the
 * status a lesson is already in — quite rightly, it drove a menu of *actions* — so gating `enabled`
 * on it alone greyed out the one segment that is selected, every single time. The control then read
 * as broken rather than as informative. Being tappable-but-inert is how a segmented control's chosen
 * option behaves anyway; what stays disabled is the genuinely unreachable transition, which is real
 * information (a completed lesson can be cancelled but not un-completed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSelector(row: ScheduleRow, onStatus: (LessonStatus) -> Unit) {
    val current = row.session.status
    val allowed = row.statusChoices
    Column(Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline)) {
        SectionTitle(
            stringResource(R.string.class_status_label),
            modifier = Modifier.padding(bottom = Spacing.inline),
        )
        SegmentedChoice(
            options = LessonStatus.entries,
            selected = current,
            onSelect = onStatus,
            // Which statuses this lesson may move to is `SessionRules.sessionTransitions`', arriving as
            // `row.statusChoices` — the same rule the swipe is offered from.
            enabled = { status -> status in allowed },
            label = { status -> classStatusLabel(status) },
        )
    }
}

/** A bottom sheet's own gutter, wider than the screen's: Material insets sheet content further in. */
internal val SheetGutter = 24.dp

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .insetRowPress { Modifier.clickable(it, indication = null, role = Role.Button) { onClick() } }
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = SheetGutter, vertical = Spacing.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(Spacing.leading))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Picks a new date, then a new time, and reports **the two the tutor chose**.
 *
 * It used to combine them here — `date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()` — which put
 * a wall-clock-to-instant conversion, and a third definition of the app's zone, inside a composable on the
 * path that changes when a lesson happens. `ScheduleViewModel.moveTo` does it now, against the injected
 * clock's zone; see `domain/billing/PaymentDate` for the same argument made about the path that records money.
 */
@Composable
internal fun MoveClassFlow(
    start: java.time.Instant,
    onMoved: (LocalDate, java.time.LocalTime) -> Unit,
    onDone: () -> Unit,
) {
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    // Held in a local so the time step can use the date without asserting it: `pickedDate` is a `by`
    // delegate over mutable state, which Kotlin re-reads on each access and so cannot smart-cast, and
    // the `null` branch below is precisely what establishes that it is set.
    val date = pickedDate
    if (date == null) {
        DatePickerModal(
            initial = start.toLocalDate(),
            onConfirm = { pickedDate = it },
            onDismiss = onDone,
        )
    } else {
        TimePickerModal(
            initial = start.toLocalTime(),
            onConfirm = { time ->
                onMoved(date, time)
                onDone()
            },
            onDismiss = onDone,
        )
    }
}
