package com.seblful.klas.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.seblful.klas.R
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberRevealedFocus

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
    val mismatch = repeat.isNotEmpty() && repeat != password
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(stringResource(R.string.backup_export_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                Text(stringResource(R.string.backup_export_password_desc))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.backup_password),
                    modifier = Modifier.focusRequester(rememberRevealedFocus()),
                )
                if (password.isNotEmpty()) {
                    PasswordField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = stringResource(R.string.backup_password_repeat),
                        error = stringResource(R.string.backup_password_mismatch).takeIf { mismatch },
                    )
                    Text(
                        stringResource(R.string.backup_password_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(password.ifEmpty { null }); onDismiss() },
                enabled = password.isEmpty() || password == repeat,
            ) {
                Text(
                    stringResource(
                        if (password.isEmpty()) R.string.backup_export_save_plain else R.string.backup_export_save,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Asks for a sealed backup's password; [wrong] once one has failed to open it. */
@Composable
fun UnlockBackupDialog(wrong: Boolean, onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(stringResource(R.string.backup_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                Text(stringResource(R.string.backup_unlock_desc))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.backup_password),
                    error = stringResource(R.string.backup_error_wrong_password).takeIf { wrong },
                    modifier = Modifier.focusRequester(rememberRevealedFocus()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.backup_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
