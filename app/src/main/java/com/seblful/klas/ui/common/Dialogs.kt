package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.window.DialogProperties
import com.seblful.klas.R

/** The app's dialogs, and the one rule they share. */

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

internal val KeepTypedInput = DialogProperties(dismissOnClickOutside = false)
/**
 * A dialog for naming a subject. Confirmation requires a non-blank name.
 *
 * @param capitalization how the keyboard starts a word — a subject's name is written as one.
 */
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
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
                keyboardOptions = KeyboardOptions(capitalization = capitalization),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value); onDismiss() },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
/**
 * A dialog that **tells** rather than asks: what happened, and «ОК» — a booking that skipped some dates,
 * an hour already taken, the typeface's licence.
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
/** One option of a one-of-many choice inside a dialog — a weekday, a subject to add. */
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
