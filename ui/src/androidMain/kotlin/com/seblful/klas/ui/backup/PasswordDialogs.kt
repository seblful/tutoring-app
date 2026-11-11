package com.seblful.klas.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.FormDialog
import com.seblful.klas.ui.theme.klasSemantics

/*
 * Passwords are held in plain `remember`, never `rememberSaveable`: saved state is written to disk when the
 * process is put away, and a backup's password has no business being there. Rotating with a dialog open
 * costs retyping it.
 */

/**
 * Asked before an export: a password to seal the file with, or none. Typing one shows what losing it costs,
 * since there is no way back without it.
 */
@Composable
fun ExportPasswordDialog(onSave: (password: String?) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    FormDialog(
        title = stringResource(Strings.backup_export_password_title),
        confirm = DialogAction(
            label = stringResource(
                if (password.isEmpty()) Strings.backup_export_save_plain else Strings.backup_export_save,
            ),
            onClick = { onSave(password.ifEmpty { null }); onDismiss() },
            enabled = password.isEmpty() || password == repeat,
        ),
        onDismiss = onDismiss,
    ) {
        ExportPasswordFields(
            password = password,
            onPassword = { password = it },
            repeat = repeat,
            onRepeat = { repeat = it },
            firstField = Modifier.focusRequester(rememberRevealedFocus()),
        )
    }
}

/**
 * The export dialog's body. Apart from the dialog so a preview can draw it: the dialog's window with a focused
 * field never settles for a screenshot.
 */
@Composable
fun ExportPasswordFields(
    password: String,
    onPassword: (String) -> Unit,
    repeat: String,
    onRepeat: (String) -> Unit,
    firstField: Modifier = Modifier,
) {
    val mismatch = repeat.isNotEmpty() && repeat != password
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Text(stringResource(Strings.backup_export_password_desc))
        PasswordField(
            value = password,
            onValueChange = onPassword,
            label = stringResource(Strings.backup_password),
            modifier = firstField,
        )
        if (password.isNotEmpty()) {
            PasswordField(
                value = repeat,
                onValueChange = onRepeat,
                label = stringResource(Strings.backup_password_repeat),
                error = stringResource(Strings.backup_password_mismatch).takeIf { mismatch },
            )
            Text(
                stringResource(Strings.backup_password_warning),
                color = klasSemantics.destructive,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Asks for a sealed backup's password; [wrong] once one has failed to open it. */
@Composable
fun UnlockBackupDialog(wrong: Boolean, onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    FormDialog(
        title = stringResource(Strings.backup_unlock_title),
        confirm = DialogAction(
            label = stringResource(Strings.backup_unlock),
            onClick = { onUnlock(password) },
            enabled = password.isNotEmpty(),
        ),
        onDismiss = onDismiss,
    ) {
        UnlockBackupFields(password, { password = it }, wrong, Modifier.focusRequester(rememberRevealedFocus()))
    }
}

/** The unlock dialog's body, apart from the dialog for the same reason as [ExportPasswordFields]. */
@Composable
fun UnlockBackupFields(
    password: String,
    onPassword: (String) -> Unit,
    wrong: Boolean,
    field: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Text(stringResource(Strings.backup_unlock_desc))
        PasswordField(
            value = password,
            onValueChange = onPassword,
            label = stringResource(Strings.backup_password),
            error = stringResource(Strings.backup_error_wrong_password).takeIf { wrong },
            modifier = field,
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
    )
}
