@file:Suppress("ForbiddenImport") // The wrapper the rule points features to.

package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.seblful.klas.lock.UnlockedWindow
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource

/**
 * A small form — at most three inputs — with Save and Cancel. A stray tap outside never throws away what was
 * typed; back and Cancel still close it. Pressing [confirm] runs it and nothing else: the caller closes the
 * dialog, since some saves close it and some wait for an answer. [extra] is a third, rarer action placed
 * beside Cancel, such as Delete in an editor.
 */
@Composable
fun FormDialog(
    title: String,
    confirm: DialogAction,
    onDismiss: () -> Unit,
    extra: DialogAction? = null,
    content: @Composable () -> Unit,
) {
    UnlockedWindow { drafts ->
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(title) },
            text = {
                drafts.SaveableStateProvider("body") {
                    Box(Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            },
            confirmButton = { ActionButton(confirm) },
            dismissButton = {
                extra?.let { ActionButton(it) }
                KlasButton(onClick = onDismiss, kind = ButtonKind.Text) { Text(stringResource(Strings.action_cancel)) }
            },
        )
    }
}

/**
 * A list to pick from, where picking **is** the answer: no Save to agree with the tap just made, only the way
 * out. The rows close the dialog themselves.
 */
@Composable
fun ChoiceDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    UnlockedWindow { drafts ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { drafts.SaveableStateProvider("body", content) },
            confirmButton = {
                KlasButton(onClick = onDismiss, kind = ButtonKind.Text) { Text(stringResource(Strings.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ActionButton(action: DialogAction) {
    KlasButton(
        onClick = action.onClick,
        kind = if (action.destructive) ButtonKind.Destructive else ButtonKind.Text,
        enabled = action.enabled,
    ) { Text(action.label) }
}
