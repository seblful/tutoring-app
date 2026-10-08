package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode

/**
 * The app's dialogs, and the one rule they share.
 *
 * A dialog the tutor **types into** does not close on a stray tap beside it ([KeepTypedInput]): losing a
 * half-typed price to a mis-tap is worse than an extra tap on «Отмена». It is `internal` rather than private
 * precisely because four dialogs elsewhere in the app need it — while it was private, two of them pointed at
 * it in a comment and re-spelled the properties by hand, which is a named rule kept only by whoever
 * remembered to read the comment.
 */

@Composable
fun ConfirmDialog(
    text: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    // Red for a confirmation that loses something — the colour the schedule sheet's «Удалить» and the
    // homework dialog's already say it in, so the last tap before a loss looks the same everywhere.
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
/**
 * Dialog behaviour for every dialog the tutor **types into**: a tap outside no longer closes it.
 *
 * The full-screen forms answer the same problem by asking «Сохранить изменения?» on the way out (see
 * `ui/common/UnsavedChanges.kt`). A dialog does not need the question — it already shows «Отмена» and
 * «Сохранить», so leaving it is a deliberate tap — it only needs to stop being closed *by accident*.
 */
internal val KeepTypedInput = DialogProperties(dismissOnClickOutside = false)
/** A dialog to edit a student's name, phone, email, optional note and default class mode. */
@Composable
fun StudentEditDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    initialNote: String,
    initialMode: ClassMode,
    onConfirm: (name: String, phone: String, email: String, note: String, mode: ClassMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // Saveable, so turning the phone keeps what was typed — a dialog that asks not to be tapped away
    // (`KeepTypedInput`) should not lose it to a rotation either.
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            // Five controls is the tallest dialog in the app, so it scrolls: with the keyboard up — or at
            // a large font scale — «Место» and the note fell off the bottom with no way to reach them,
            // an `AlertDialog`'s text slot having no scroll of its own.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.inline),
            ) {
                StudentContactFields(
                    name = name,
                    onName = { name = it },
                    phone = phone,
                    onPhone = { phone = it },
                    email = email,
                    onEmail = { email = it },
                    note = note,
                    onNote = { note = it },
                    mode = mode,
                    onMode = { mode = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), phone.trim(), email.trim(), note.trim(), mode); onDismiss() },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
/**
 * A dialog with a single text field. Used for naming subjects, entering amounts, etc.
 *
 * @param keyboardType numeric for amounts, text otherwise.
 * @param capitalization how the keyboard starts a word — a subject's name is written as one.
 * @param validate gates the confirm button (in addition to non-blank). For amounts this prevents
 *   silently dismissing on an unparseable value — the action stays disabled until the input is valid.
 */
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    validate: (String) -> Boolean = { true },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    // Its one field is the whole of it, and it opens because something was tapped to name a thing.
    val focus = rememberRevealedFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap beside the dialog must not throw away what was typed into it — the same rule
        // the full-screen forms follow with their «Сохранить изменения?» prompt, kept cheap here:
        // a dialog already has «Отмена», so the fix is to stop *accidental* dismissal rather than to
        // ask about a deliberate one. Back still closes it, as it closes any dialog.
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value); onDismiss() },
                enabled = value.isNotBlank() && validate(value),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
/**
 * A dialog that **tells** rather than asks: what happened, and «ОК» — a booking that skipped some dates,
 * an hour already taken, the typeface's licence.
 *
 * One button, because there is nothing to decide: the thing it reports has already happened or been
 * refused, and a «Отмена» beside «ОК» would offer to cancel a fact.
 */
@Composable
fun NoticeDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    text: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = text,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
/** [NoticeDialog] for a notice that is one string. */
@Composable
fun NoticeDialog(text: String, onDismiss: () -> Unit, title: String? = null) {
    NoticeDialog(onDismiss = onDismiss, title = title) { Text(text) }
}
/**
 * One option of a one-of-many choice inside a dialog — a weekday, a subject to add.
 *
 * The **row** is the control and the radio only its picture: a 20 dp radio is not a touch target, and a
 * second target of its own made TalkBack stop twice on every option. The radio sits flush with the
 * dialog's text, so the press reaches out past it ([outsetRowPress]) rather than cutting through it.
 */
@Composable
fun DialogRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .outsetRowPress { source ->
                Modifier.selectable(
                    selected = selected,
                    interactionSource = source,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
            }
            .heightIn(min = MinTouchTarget)
            .padding(vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = Spacing.inline))
    }
}
