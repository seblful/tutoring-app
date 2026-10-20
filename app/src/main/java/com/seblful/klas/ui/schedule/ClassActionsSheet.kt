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
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

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
            // Header: which lesson these actions apply to — laid out as the card that was tapped is, the
            // icon beside the name and the status line under both, not the icon centred on the two lines.
            Column(Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = Spacing.tight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LessonKindIcon(LessonKind.of(klass), Modifier.padding(end = Spacing.inline))
                    Text(
                        bulletJoin(klass.start.timeText(), row.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // The lesson card's own second line — status first — so the sheet opens on the
                // row that was tapped, in the same order, rather than a rearrangement of it.
                StatusLine(row, running, maxLines = Int.MAX_VALUE)
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
            SheetAction(stringResource(R.string.action_edit), KlasIcons.Outlined.Edit, onClick = onEdit)
            // Move (reschedule) is offered for any status: moving a lesson to the future re-plans it,
            // moving it to the past just corrects its recorded time. A group lesson moves as one.
            SheetAction(stringResource(R.string.class_move), KlasIcons.Outlined.EditCalendar, onClick = onMove)
            // Delete is offered for any status. Deleting a completed class removes its recognized
            // income (the confirm dialog warns about this); the repository deletes the calendar
            // event and refreshes reminders regardless of status.
            HorizontalDivider(Modifier.padding(vertical = Spacing.inline))
            SheetAction(
                stringResource(R.string.action_delete),
                KlasIcons.Outlined.Delete,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

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

private const val SheetHomeworkLines = 4

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

@Composable
internal fun MoveClassFlow(
    start: Instant,
    onMoved: (LocalDate, LocalTime) -> Unit,
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
