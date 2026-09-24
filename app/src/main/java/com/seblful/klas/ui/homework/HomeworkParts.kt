package com.seblful.klas.ui.homework

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.HomeworkRules
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.ChoiceField
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.SwipeToConfirmBox
import com.seblful.klas.ui.common.armedPop
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.theme.klasColors

/**
 * A card's homework, inside the card's own list — a student's or a group's: an add row, the current ones
 * (swiped done, as in the tab), then the done ones under their own heading (swiped back). Tapping one
 * corrects or deletes it. Nested, so rows rather than cards — see [SwipeableHomework].
 *
 * Its keys are [HomeworkItemKeys], namespaced apart from whatever else the card's list holds.
 */
fun LazyListScope.homeworkItems(
    lists: HomeworkLists,
    messages: Messages,
    onAdd: () -> Unit,
    onOpen: (HomeworkRow) -> Unit,
    onDone: (Homework, Boolean) -> Unit,
) {
    if (lists.current.isEmpty() && lists.archive.isEmpty()) {
        item(key = HomeworkItemKeys.EMPTY) {
            EmptyState(
                title = stringResource(R.string.student_homework_empty),
                icon = Icons.AutoMirrored.Outlined.Assignment,
                actionLabel = stringResource(R.string.homework_add),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = HomeworkItemKeys.ADD) { AddRow(stringResource(R.string.homework_add), onAdd) }
    items(lists.current, key = { HomeworkItemKeys.row(it.homework.id) }) { row ->
        SwipeableHomework(
            row = row,
            onSwipe = { onDone(row.homework, true) },
            onClick = { onOpen(row) },
            messages = messages,
            nested = true,
            modifier = Modifier.animateItem(),
        )
    }
    if (lists.archive.isNotEmpty()) {
        item(key = HomeworkItemKeys.DONE) {
            Text(
                stringResource(R.string.student_homework_done),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.inline)
                    .animateItem(),
            )
        }
    }
    items(lists.archive, key = { HomeworkItemKeys.row(it.homework.id) }) { row ->
        SwipeableHomework(
            row = row,
            onSwipe = { onDone(row.homework, false) },
            onClick = { onOpen(row) },
            messages = messages,
            nested = true,
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * Stable keys for [homeworkItems]. A piece of homework is keyed by its own row's id — one key whichever of
 * the two lists it is in, so a swipe between them moves the row rather than removing one and inserting
 * another — and namespaced, since a card's list also holds lessons and payments whose ids start at 1 too.
 */
object HomeworkItemKeys {
    const val EMPTY = "homework:empty"
    const val ADD = "homework:add"
    const val DONE = "homework:done"

    fun row(id: Long): String = "homework:$id"
}

/**
 * One piece of homework that is **swiped to change lists** — current to done, or done back to current —
 * the gesture a lesson is marked taught with, for the same reason: it is done after every lesson, and
 * the row is where the tutor is already looking. [SwipeToConfirmBox] owns the whole contract (a tick at
 * the mark, taken back by sliding home). Tapping it opens it, to correct or delete.
 *
 * @param nested whether it stands in a list inside a card (a student's), where a row is a `ListItem` and
 *   names who it is for only when that is a group; at the top level (the tab) it is a card of its own.
 */
@Composable
fun SwipeableHomework(
    row: HomeworkRow,
    onSwipe: () -> Unit,
    onClick: () -> Unit,
    messages: Messages,
    nested: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (nested) RectangleShape else MaterialTheme.shapes.medium
    SwipeToConfirmBox(
        onConfirm = onSwipe,
        modifier = modifier,
        reveal = { armed -> SwipeReveal(done = row.isCurrent, armed = armed, shape = shape) },
    ) {
        if (nested) HomeworkListItem(row, messages, onClick) else HomeworkCard(row, messages, onClick)
    }
}

/**
 * What the swipe promises, behind the row: «Сделано» in the green a taught lesson's panel uses, or
 * «Вернуть» in a quiet tone for one coming back out of the archive.
 */
@Composable
private fun SwipeReveal(done: Boolean, armed: Boolean, shape: Shape) {
    val (fill, ink) = if (done) {
        klasColors.successContainer to klasColors.onSuccessContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val label = stringResource(if (done) R.string.homework_done else R.string.homework_restore)
    Row(
        Modifier.fillMaxSize().background(fill, shape).padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Undo,
            contentDescription = label,
            tint = ink,
            modifier = Modifier.armedPop(armed),
        )
        Spacer(Modifier.width(Spacing.inline))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** The tab's card: who it is for, when it is for, the text — and the button that sends it. */
@Composable
private fun HomeworkCard(row: HomeworkRow, messages: Messages, onClick: () -> Unit) {
    ContentCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LessonKindIcon(
                if (row.isGroup) LessonKind.GROUP else LessonKind.INDIVIDUAL,
                Modifier.padding(end = Spacing.inline),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DueLine(row)
            }
            if (row.isCurrent) SendHomeworkButton(row, messages)
        }
        Text(
            row.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.inline),
        )
    }
}

/**
 * A student card's row: the text first, since the card already says whose it is — and the group's name
 * beside the day when it is a group's, since that is the one thing the card does not say.
 */
@Composable
private fun HomeworkListItem(row: HomeworkRow, messages: Messages, onClick: () -> Unit) {
    ListItem(
        leadingContent = { LessonKindIcon(if (row.isGroup) LessonKind.GROUP else LessonKind.INDIVIDUAL) },
        headlineContent = { Text(row.text) },
        supportingContent = { DueLine(row, prefix = row.title.takeIf { row.isGroup }) },
        trailingContent = if (row.isCurrent) ({ SendHomeworkButton(row, messages) }) else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * When it is for, as the case it is in — [HomeworkRow.due] decides, this only words it: overdue in the
 * error colour, since that is the one of the four that asks for something; done ones say when they were
 * set instead.
 */
@Composable
private fun DueLine(row: HomeworkRow, prefix: String? = null) {
    val at = row.lessonAt
    val text = when (row.due) {
        HomeworkRules.Due.OVERDUE -> stringResource(R.string.homework_overdue, at?.dateTimeText().orEmpty())
        HomeworkRules.Due.TODAY -> stringResource(R.string.homework_due_today, at?.timeText().orEmpty())
        HomeworkRules.Due.LATER -> stringResource(R.string.homework_due, at?.dateTimeText().orEmpty())
        HomeworkRules.Due.UNBOOKED -> stringResource(R.string.homework_unbooked)
        null -> stringResource(R.string.homework_set_on, row.homework.givenAt.formatDate())
    }
    Text(
        bulletJoin(prefix.orEmpty(), text),
        style = MaterialTheme.typography.bodySmall,
        color = if (row.due == HomeworkRules.Due.OVERDUE) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Setting homework, or correcting it.
 *
 * **Who, what, and — for a new one — where it will go.** The line under the text names the lesson it will
 * be attached to, so the rule («к следующему занятию») is visible at the moment it applies rather than
 * something to trust. Editing leaves out that line: when it was given stays, so the lesson does too.
 *
 * @param choices who it can be for, whoever is being taught about now at the top
 *   (`HomeworkRules.byNearestLesson`). Never empty. **Nobody is preselected for a new one** — a name
 *   already in the field is one a quick «Сохранить» sends the homework to, so the tutor picks every time.
 * @param editing the homework being corrected, or null for a new one.
 * @param canChangeAssignee false where the screen already says who (a student's card): the field is shown
 *   filled and fixed, since there is nothing to pick.
 * @param onDelete offered only while [editing].
 */
@Composable
fun HomeworkDialog(
    choices: List<AssigneeChoice>,
    onSave: (Assignee, String) -> Unit,
    onDismiss: () -> Unit,
    editing: Homework? = null,
    canChangeAssignee: Boolean = true,
    onDelete: (() -> Unit)? = null,
) {
    var selected by remember {
        mutableStateOf(
            choices.firstOrNull { it.assignee == editing?.assignee }
                ?: choices.first().takeIf { !canChangeAssignee },
        )
    }
    var text by rememberSaveable { mutableStateOf(editing?.text.orEmpty()) }
    // Only a correction opens in the text. A new one opens on nothing: who it is for comes first, and the
    // keyboard it used to raise asked for the homework before that and covered the dialog doing it.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editing != null) focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap beside the dialog must not throw away what was just typed.
        properties = KeepTypedInput,
        title = { Text(stringResource(if (editing == null) R.string.homework_add else R.string.homework_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                ChoiceField<AssigneeChoice?>(
                    label = stringResource(R.string.homework_assignee_label),
                    selected = selected,
                    options = choices,
                    optionLabel = { it?.name.orEmpty() },
                    onSelect = { selected = it },
                    enabled = canChangeAssignee,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.homework_text_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = HomeworkDialogLines,
                    supportingText = selected?.takeIf { editing == null }?.let { choice -> { Text(attachedText(choice)) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            val chosen = selected
            TextButton(
                onClick = { if (chosen != null) { onSave(chosen.assignee, text); onDismiss() } },
                enabled = chosen != null && text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (editing != null && onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Which lesson a new piece of homework will be attached to — or that there is none to attach it to yet. */
@Composable
private fun attachedText(choice: AssigneeChoice): String =
    choice.nextLessonAt?.let { stringResource(R.string.homework_attached_to, it.dateTimeText()) }
        ?: stringResource(R.string.homework_attached_none)

/** An assignment and a line of what to watch for, before the field starts to grow. */
private const val HomeworkDialogLines = 3

/**
 * «Отправить»: the homework, worded for the student, into the phone's share sheet — a messenger, in
 * practice, which is where a tutor sends it.
 *
 * Through the share sheet rather than at the student's phone number: which messenger a family uses is
 * theirs, and the sheet already knows every one on the phone.
 */
@Composable
fun SendHomeworkButton(row: HomeworkRow, messages: Messages) {
    val context = LocalContext.current
    val text = homeworkShareText(row)
    val chooserTitle = stringResource(R.string.homework_send)
    val failed = stringResource(R.string.student_contact_failed)
    HoldToName(chooserTitle) {
        IconButton(onClick = { sendHomework(context, text, chooserTitle, failed, messages) }) {
            Icon(Icons.Filled.Share, contentDescription = chooserTitle, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The homework as the student reads it: with the day it is for when a lesson is booked to bring it to,
 * and on its own when none is yet. A date and not a time, since the time is the lesson's and they know it.
 */
@Composable
fun homeworkShareText(row: HomeworkRow): String =
    row.lessonAt
        ?.let { stringResource(R.string.homework_share_due, it.formatDate(), row.text) }
        ?: stringResource(R.string.homework_share, row.text)

private fun sendHomework(context: Context, text: String, chooserTitle: String, failed: String, messages: Messages) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    launchExternally(context, Intent.createChooser(send, chooserTitle), failed, messages)
}
