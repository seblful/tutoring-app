package com.seblful.klas.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.format.toLocalTime
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.Reveal
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.SheetHandle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.rememberToggleFeedback
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeRangeText
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.ChoiceDialog
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.component.KlasSheet
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * The lesson sheet (ActionSheet): which lesson, then what is done to it most — marked taught, rescheduled,
 * edited — then who came, then where it leads, then the rarer changes of status, and Delete last. Status is
 * acted on, not chosen from segments: «Taught» is the main button; «Cancel lesson» and «Return to planned»
 * are rows, each with the guarded Undo its message offers (B2), and a change that cannot apply says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassActionsSheet(
    row: ScheduleRow,
    phase: LessonPhase,
    sheetState: androidx.compose.material3.SheetState,
    homework: List<SheetHomework>,
    onHomeworkDone: (Homework, Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatus: (LessonStatus) -> Unit,
    onAttendance: (TutoringClass, Attended) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSetHomework: () -> Unit = {},
    onRecordPayment: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onCorrectCompletion: () -> Unit = {},
    onBookNext: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // No `dragHandle`: Material wraps whatever is passed there in a clickable, tooltipped box of its own,
    // so even a plain pill lit grey when held. The pill is the sheet's first row instead — see
    // [SheetHandle]; the whole sheet drags, not only the handle.
    KlasSheet(onDismiss = onDismiss, state = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = SheetGutter)) {
            SheetHandle(
                Modifier.align(Alignment.CenterHorizontally),
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            )
            SheetHeader(row, phase)
            FrequentActions(row, onTaught = { onStatus(LessonStatus.COMPLETED) }, onMove = onMove, onEdit = onEdit)
            LessonAttendance(row, onAttendance)
            // What was set for this lesson, **with its ticks on the sheet**: the start of a lesson is when
            // the tutor asks for it, and this is the screen they are on. Read once the sheet is open, so it
            // grows in rather than lengthening the sheet in one frame.
            Reveal(homework.isNotEmpty()) { AttachedHomework(homework, onHomeworkDone) }
            KlasDivider(Modifier.padding(vertical = Spacing.inline))
            SheetAction(stringResource(R.string.homework_add), KlasIcons.Outlined.Assignment, onSetHomework)
            SheetAction(stringResource(R.string.add_payment), KlasIcons.Outlined.Payments, onRecordPayment)
            // One ordinary lesson a week later, prefilled — safe, because it writes nothing until booked.
            SheetAction(stringResource(R.string.class_book_next), KlasIcons.Outlined.CalendarMonth, onBookNext)
            SheetAction(
                stringResource(if (row.isGroup) R.string.class_open_group else R.string.class_open_student),
                if (row.isGroup) KlasIcons.Outlined.Group else KlasIcons.Outlined.Person,
                onOpenProfile,
                opens = true,
            )
            KlasDivider(Modifier.padding(vertical = Spacing.inline))
            StatusRows(row, onStatus, onCorrectCompletion)
            // Delete is offered for any status. Deleting a taught lesson removes what it earned (the confirm
            // warns of it); the repository deletes the calendar event and refreshes reminders regardless.
            SheetAction(
                stringResource(R.string.action_delete),
                KlasIcons.Outlined.Delete,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

/**
 * Which lesson: the day and its hours in full first — opened from the week, a reminder or Today, «16:00»
 * alone did not say which day — then who, then the card's own status line with the rate.
 */
@Composable
private fun SheetHeader(row: ScheduleRow, phase: LessonPhase) {
    val klass = row.klass
    Column(Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = Spacing.tight)) {
        Text(
            bulletJoin(
                klass.start.toLocalDate().formatDay(),
                klass.start.timeRangeText(klass.durationMinutes),
                durationLabel(klass.durationMinutes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            if (row.isGroup) LessonKindIcon(LessonKind.GROUP, Modifier.padding(end = Spacing.inline))
            Text(row.title, style = MaterialTheme.typography.titleMedium)
        }
        val rate = stringResource(R.string.price_per_hour, klass.price.text())
        StatusLine(row, phase, maxLines = Int.MAX_VALUE, rate = rate)
    }
}

/**
 * The three things done to a lesson most, as buttons: «Taught» filled (R3), «Reschedule» and «Edit» tonal.
 * A taught lesson's button stays, ticked and spent; a cancelled one's says why it cannot be marked yet.
 * They wrap rather than squeeze: at large text the three do not fit one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequentActions(row: ScheduleRow, onTaught: () -> Unit, onMove: () -> Unit, onEdit: () -> Unit) {
    val status = row.session.status
    val taught = status == LessonStatus.COMPLETED
    Column(Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
            verticalArrangement = Arrangement.spacedBy(Spacing.inline),
        ) {
            KlasButton(
                onClick = onTaught,
                enabled = !taught && LessonStatus.COMPLETED in row.statusChoices,
                withIcon = true,
            ) {
                Icon(
                    if (taught) KlasIcons.Filled.CheckCircle else KlasIcons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.tight),
                )
                Text(classStatusLabel(LessonStatus.COMPLETED))
            }
            // Moving to the future re-plans it; to the past only corrects when it happened. A group moves as one.
            KlasButton(onClick = onMove, kind = ButtonKind.Tonal) { Text(stringResource(R.string.class_move)) }
            // Editing is allowed in any status; saving a taught lesson re-bills it (see ClassRules.rebill).
            KlasButton(onClick = onEdit, kind = ButtonKind.Tonal) { Text(stringResource(R.string.action_edit)) }
        }
        if (status == LessonStatus.CANCELLED) {
            Text(
                stringResource(R.string.class_taught_after_replan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
    }
}

/**
 * The rarer changes of status, as rows: a planned or taught lesson can be cancelled; a taught one returns to
 * planned through the documented correction (its confirm, then Undo); a cancelled one returns to planned.
 */
@Composable
private fun StatusRows(row: ScheduleRow, onStatus: (LessonStatus) -> Unit, onCorrectCompletion: () -> Unit) {
    val status = row.session.status
    val allowed = row.statusChoices
    when (status) {
        LessonStatus.COMPLETED ->
            SheetAction(stringResource(R.string.class_correct_planned), KlasIcons.Filled.Undo, onCorrectCompletion)
        LessonStatus.CANCELLED -> if (LessonStatus.SCHEDULED in allowed) {
            SheetAction(
                stringResource(R.string.class_correct_planned),
                KlasIcons.Filled.Undo,
                onClick = { onStatus(LessonStatus.SCHEDULED) },
            )
        }
        LessonStatus.SCHEDULED -> Unit
    }
    if (status != LessonStatus.CANCELLED && LessonStatus.CANCELLED in allowed) {
        SheetAction(
            stringResource(R.string.class_cancel_lesson),
            KlasIcons.Outlined.EventBusy,
            onClick = { onStatus(LessonStatus.CANCELLED) },
        )
    }
}

/** A group's payment belongs to one student, so the tutor names the payer first. */
@Composable
internal fun LessonPayerDialog(row: ScheduleRow, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(title = stringResource(R.string.payment_choose_student), onDismiss = onDismiss) {
        // A simple dialog's list: rows to pick from, not a stack of buttons.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            row.classes.forEachIndexed { index, klass ->
                val name = row.members.getOrNull(index).orEmpty()
                ListItem(
                    headlineContent = { Text(name) },
                    leadingContent = { Avatar(name, klass.studentId) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.outsetRowPress { source ->
                        Modifier.clickable(source, indication = null, role = Role.Button) {
                            onSelect(klass.studentId); onDismiss()
                        }
                    },
                )
            }
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

internal val SheetGutter = 24.dp

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
    opens: Boolean = false,
) {
    val color = if (destructive) klasSemantics.destructive else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        // A chevron only where the row goes to another screen (R6).
        trailingContent = if (opens) ({ OpensScreenMark() }) else null,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = color,
            leadingIconColor = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        // The list item's own 16 dp inset finishes the sheet's gutter, so the label lines up with the header.
        modifier = Modifier
            .insetRowPress { Modifier.clickable(it, indication = null, role = Role.Button) { onClick() } }
            .padding(horizontal = SheetGutter - Spacing.gutter),
    )
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
