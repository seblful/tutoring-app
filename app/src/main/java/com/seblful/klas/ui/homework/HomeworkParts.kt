package com.seblful.klas.ui.homework

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.ChoiceField
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.SwipeReveal
import com.seblful.klas.ui.common.SwipeToConfirmBox
import com.seblful.klas.ui.common.SwipeToward
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberExternalApps
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.common.swipeAction
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.FormDialog
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasColors
import com.seblful.klas.ui.theme.klasSemantics

/**
 * A card's homework, inside the card's own list — a student's or a group's: an add row, the current ones
 * (swiped done, as in the tab), then the done ones under their own heading (swiped back). Tapping one
 * corrects or deletes it. Nested, so rows rather than cards — see [SwipeableHomework].
 *
 * @param owner whose card this is. A row names who its homework is for only when that is someone else —
 * a group's homework on a member's card — not on every row of the group's own card.
 */
fun LazyListScope.homeworkItems(
    owner: Assignee?,
    lists: HomeworkLists,
    messages: Messages,
    onAdd: () -> Unit,
    onOpen: (HomeworkRow) -> Unit,
    onDone: (Homework, Boolean) -> Unit,
) {
    if (lists.current.isEmpty() && lists.archive.isEmpty()) {
        item(key = HomeworkItemKeys.EMPTY, contentType = HomeworkItemKeys.EMPTY) {
            EmptyState(
                title = stringResource(R.string.student_homework_empty),
                icon = KlasIcons.Outlined.Assignment,
                actionLabel = stringResource(R.string.homework_add),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = HomeworkItemKeys.ADD, contentType = HomeworkItemKeys.ADD) {
        AddRow(stringResource(R.string.homework_add), onAdd)
    }
    items(
        lists.current,
        key = { HomeworkItemKeys.row(it.homework.id) },
        contentType = { HomeworkItemKeys.ROW_TYPE },
    ) { row ->
        SwipeableHomework(
            row = row,
            onSwipe = { onDone(row.homework, true) },
            onClick = { onOpen(row) },
            messages = messages,
            nested = true,
            owner = owner,
            modifier = animateInPlace(),
        )
    }
    if (lists.archive.isNotEmpty()) {
        item(key = HomeworkItemKeys.DONE, contentType = HomeworkItemKeys.DONE) {
            // A section of the tab, so a section title — and a heading a reader can jump to, which it
            // was not while it was a `Text` styled to look like one.
            SectionTitle(
                stringResource(R.string.student_homework_done),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.inline)
                    .then(animateInPlace()),
            )
        }
    }
    items(
        lists.archive,
        key = { HomeworkItemKeys.row(it.homework.id) },
        contentType = { HomeworkItemKeys.ROW_TYPE },
    ) { row ->
        SwipeableHomework(
            row = row,
            onSwipe = { onDone(row.homework, false) },
            onClick = { onOpen(row) },
            messages = messages,
            nested = true,
            owner = owner,
            modifier = animateInPlace(),
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

    /** Every row's content type — the empty state, add row and heading are each their own. */
    const val ROW_TYPE = "homework:row"

    fun row(id: Long): String = "homework:$id"
}

/**
 * One piece of homework that is **swiped to change lists** — current to done, or done back to current —
 * the gesture a lesson is marked taught with, for the same reason: it is done after every lesson, and
 * the row is where the tutor is already looking. [SwipeToConfirmBox] owns the whole contract (a tick at
 * the mark, taken back by sliding home).
 *
 * @param nested whether it stands in a list inside a card (a student's), where a row is a `ListItem` and
 * names who it is for only when that is a group; at the top level (the tab) it is a card of its own.
 * @param owner the card a nested row stands in; its own homework goes unnamed — see [homeworkItems].
 * @param container what a nested row stands on, painted under it to cover the swipe's panel: the page, or
 * the section card it is listed in.
 */
@Composable
fun SwipeableHomework(
    row: HomeworkRow,
    onSwipe: () -> Unit,
    onClick: () -> Unit,
    messages: Messages,
    nested: Boolean,
    modifier: Modifier = Modifier,
    owner: Assignee? = null,
    container: Color = ListItemDefaults.containerColor,
    picking: HomeworkPicking = HomeworkPicking.NONE,
) {
    if (picking.active && !nested) {
        HomeworkCard(row, messages, { picking.toggle(row) }, onSwipe, modifier, picking)
        return
    }
    val shape = if (nested) RectangleShape else MaterialTheme.shapes.medium
    // Toward the list it goes to: done is the tab on the right, current the one on the left. So a row
    // always takes the way its tab cannot turn, and the other way still turns it.
    val toward = if (row.isCurrent) SwipeToward.End else SwipeToward.Start
    SwipeToConfirmBox(
        onConfirm = onSwipe,
        modifier = modifier,
        toward = toward,
        reveal = { armed -> HomeworkReveal(done = row.isCurrent, armed = armed, shape = shape, toward = toward) },
    ) {
        // The swipe's twin for a screen reader, on the same node it reads and taps.
        val swipe = Modifier.swipeAction(
            stringResource(if (row.isCurrent) R.string.homework_done else R.string.homework_restore),
            onSwipe,
        )
        val named = row.isGroup && row.homework.assignee != owner
        if (nested) {
            HomeworkListItem(row, named, messages, onClick, onSwipe, container, swipe)
        } else {
            HomeworkCard(row, messages, onClick, onSwipe, swipe, picking)
        }
    }
}

@Composable
private fun HomeworkReveal(done: Boolean, armed: Boolean, shape: Shape, toward: SwipeToward) {
    val (fill, ink) = if (done) {
        klasColors.successContainer to klasColors.onSuccessContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    SwipeReveal(
        icon = if (done) KlasIcons.Filled.CheckCircle else KlasIcons.Filled.Undo,
        label = stringResource(if (done) R.string.homework_done else R.string.homework_restore),
        container = fill,
        content = ink,
        armed = armed,
        shape = shape,
        horizontalPadding = Spacing.gutter,
        toward = toward,
    )
}

@Composable
private fun HomeworkCard(
    row: HomeworkRow,
    messages: Messages,
    onClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
    picking: HomeworkPicking,
) {
    ContentCard(
        onClick = onClick,
        modifier = modifier,
        selected = picking.isPicked(row),
        onLongClick = if (picking.enabled) ({ picking.toggle(row) }) else null,
        onLongClickLabel = stringResource(R.string.batch_select),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeworkTick(row, onDone)
            // **The task leads**: it is what the tutor scans for; whom it is for and when follow it.
            Column(Modifier.weight(1f).padding(horizontal = Spacing.inline)) {
                // A few lines, not the whole of a pasted exercise list: the card is also what is swiped, and a
                // card several screens tall is a poor target. The dialog holds the full text.
                Text(
                    row.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = HomeworkPreviewLines,
                    overflow = TextOverflow.Ellipsis,
                )
                DueLine(row, prefix = row.title, underSection = true)
            }
            HomeworkMenu(row, messages)
        }
    }
}

@Composable
private fun HomeworkListItem(
    row: HomeworkRow,
    named: Boolean,
    messages: Messages,
    onClick: () -> Unit,
    onDone: () -> Unit,
    container: Color,
    modifier: Modifier,
) {
    ListItem(
        leadingContent = { HomeworkTick(row, onDone) },
        headlineContent = { Text(row.text, maxLines = HomeworkPreviewLines, overflow = TextOverflow.Ellipsis) },
        supportingContent = { DueLine(row, prefix = row.title.takeIf { named }) },
        trailingContent = { HomeworkMenu(row, messages) },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier
            // Edge to edge, under the inset press shape: the swipe's panel lies behind this row, and the
            // list item's own fill, clipped to that shape, stops 8 dp short of each side — the first pixels
            // of a drag showed the panel down both edges.
            .background(container)
            .insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) }
            .then(modifier),
    )
}

private const val HomeworkPreviewLines = 4

/**
 * Whom it is for and the lesson it is for — never «due»: homework has no date of its own, only its lesson's
 * (domain-rules). [underSection]: under the tab's section titles, which already say which lessons, the line
 * is the lesson's time alone; in a student's card, which has none, it says «Lesson Fri 16:00».
 */
@Composable
private fun DueLine(row: HomeworkRow, prefix: String? = null, underSection: Boolean = false) {
    val at = row.lessonAt
    val text = when (row.due) {
        HomeworkRules.Due.TODAY ->
            if (underSection) at?.timeText().orEmpty()
            else stringResource(R.string.homework_lesson_today, at?.timeText().orEmpty())
        HomeworkRules.Due.OVERDUE, HomeworkRules.Due.LATER ->
            if (underSection) at?.dateTimeText().orEmpty()
            else stringResource(R.string.homework_lesson_at, at?.dateTimeText().orEmpty())
        HomeworkRules.Due.UNBOOKED -> if (underSection) "" else stringResource(R.string.homework_unbooked)
        null -> stringResource(R.string.homework_set_on, row.homework.givenAt.formatDate())
    }
    Text(
        bulletJoin(prefix.orEmpty(), text),
        style = MaterialTheme.typography.bodySmall,
        color = if (row.due == HomeworkRules.Due.OVERDUE) klasSemantics.overdue
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Setting homework, or correcting it.
 *
 * @param choices who it can be for, whoever is being taught about now at the top
 * (`HomeworkRules.byNearestLesson`). Never empty. **Nobody is preselected for a new one** — a name
 * already in the field is one a quick «Сохранить» sends the homework to, so the tutor picks every time.
 * @param editing the homework being corrected, or null for a new one.
 * @param canChangeAssignee false where the screen already says who (a student's card): the field is shown
 * filled and fixed, since there is nothing to pick.
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
    val focus = if (editing != null) rememberRevealedFocus() else null
    val chosen = selected
    FormDialog(
        title = stringResource(if (editing == null) R.string.homework_add else R.string.homework_label),
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = { if (chosen != null) { onSave(chosen.assignee, text); onDismiss() } },
            enabled = chosen != null && text.isNotBlank(),
        ),
        onDismiss = onDismiss,
        extra = if (editing != null && onDelete != null) {
            DialogAction(
                label = stringResource(R.string.action_delete),
                onClick = { onDelete(); onDismiss() },
                destructive = true,
            )
        } else {
            null
        },
    ) {
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
                supportingText = selected?.takeIf {
                    editing == null || it.assignee != editing.assignee
                }?.let { choice -> { Text(attachedText(choice)) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
    }
}

/**
 * The two homework writes every list of it lets the tutor take back — a swipe between current and done,
 * and a delete — each followed by the message that carries «Отменить».
 */
class HomeworkUndo internal constructor(
    private val messages: Messages,
    private val words: Words,
    private val setDone: (Homework, Boolean) -> Unit,
    private val putBack: (Homework) -> Unit,
    private val delete: (Homework) -> Unit,
    private val restore: (Homework) -> Unit,
) {
    /** [item] swiped onto the done list, or back off it — put back exactly as it was if taken back. */
    fun swiped(item: Homework, done: Boolean) {
        setDone(item, done)
        messages.show(if (done) words.done else words.restored, words.undo) { putBack(item) }
    }

    /** [item] deleted from its dialog. */
    fun deleted(item: Homework) {
        delete(item)
        messages.show(words.deleted, words.undo) { restore(item) }
    }

    internal data class Words(val done: String, val restored: String, val deleted: String, val undo: String)
}

/**
 * A [HomeworkUndo] over a view model's homework writes, saying itself through [messages].
 *
 * The same instance across recompositions: every card's swipe captures it, so a new one each pass would
 * recompose every visible card with the screen. The writes are read at the call, so a caller's new
 * function references need not keep their identity.
 */
@Composable
fun homeworkUndo(
    messages: Messages,
    setDone: (Homework, Boolean) -> Unit,
    putBack: (Homework) -> Unit,
    delete: (Homework) -> Unit,
    restore: (Homework) -> Unit,
): HomeworkUndo {
    val words = HomeworkUndo.Words(
        done = stringResource(R.string.homework_done_message),
        restored = stringResource(R.string.homework_restored_message),
        deleted = stringResource(R.string.homework_deleted),
        undo = stringResource(R.string.action_undo),
    )
    val currentSetDone by rememberUpdatedState(setDone)
    val currentPutBack by rememberUpdatedState(putBack)
    val currentDelete by rememberUpdatedState(delete)
    val currentRestore by rememberUpdatedState(restore)
    return remember(messages, words) {
        HomeworkUndo(
            messages = messages,
            words = words,
            setDone = { item, done -> currentSetDone(item, done) },
            putBack = { currentPutBack(it) },
            delete = { currentDelete(it) },
            restore = { currentRestore(it) },
        )
    }
}

/**
 * A student's or a group's card's homework dialogs: giving a new piece to whoever the card is about, and
 * correcting or deleting one opened from its list.
 *
 * @param adding who a new piece is for, taken as the dialog opened — or null while none is being given.
 * @param opened the piece being corrected, or null.
 * @param onDismiss closes whichever is open.
 */
@Composable
fun CardHomeworkDialogs(
    adding: AssigneeChoice?,
    opened: HomeworkRow?,
    onDismiss: () -> Unit,
    onGive: (String) -> Unit,
    onEdit: (Homework, String) -> Unit,
    undo: HomeworkUndo,
) {
    adding?.let { choice ->
        HomeworkDialog(
            choices = listOf(choice),
            onSave = { _, text -> onGive(text) },
            onDismiss = onDismiss,
            canChangeAssignee = false,
        )
    }
    opened?.let { row ->
        HomeworkDialog(
            choices = listOf(AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)),
            editing = row.homework,
            canChangeAssignee = false,
            onSave = { _, text -> onEdit(row.homework, text) },
            onDelete = { undo.deleted(row.homework) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun attachedText(choice: AssigneeChoice): String =
    choice.nextLessonAt?.let { stringResource(R.string.homework_attached_to, it.dateTimeText()) }
        ?: stringResource(R.string.homework_attached_none)

private const val HomeworkDialogLines = 3

/**
 * A row's leading tick (R1, as on Today and the widget): marks current homework done, or a done piece not
 * done — the swipe, for whoever does not know to swipe.
 */
@Composable
private fun HomeworkTick(row: HomeworkRow, onDone: () -> Unit) {
    NamedIconButton(
        if (row.isCurrent) KlasIcons.Outlined.Circle else KlasIcons.Filled.CheckCircle,
        stringResource(if (row.isCurrent) R.string.homework_done else R.string.homework_restore),
        onClick = onDone,
        tint = if (row.isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
    )
}

/**
 * «⋮» with «Отправить» while it is current — the row's one trailing slot. Send puts the homework, worded
 * for the student, into the phone's share sheet: a messenger, in practice, which is where a tutor sends it.
 */
@Composable
private fun HomeworkMenu(row: HomeworkRow, messages: Messages) {
    if (!row.isCurrent) return
    val externalApps = rememberExternalApps(messages)
    val text = homeworkShareText(row)
    val chooserTitle = stringResource(R.string.homework_send)
    val failed = stringResource(R.string.student_contact_failed)
    OverflowMenu { close ->
        MenuItem(
            text = { Text(chooserTitle) },
            leadingIcon = { Icon(KlasIcons.Outlined.Share, contentDescription = null) },
            onClick = {
                close()
                externalApps.open(ExternalTarget.ShareText(text, chooserTitle), failed)
            },
        )
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
