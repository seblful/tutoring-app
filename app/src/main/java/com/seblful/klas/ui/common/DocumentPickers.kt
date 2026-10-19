package com.seblful.klas.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.seblful.klas.storage.DocumentRef

/*
 * The system file picker, as a screen asks for it: a function to call with what the picker needs, and an
 * answer that is already a [DocumentRef]. The `Uri` the platform hands back exists only inside these two
 * functions, so a screen — like the view models behind it — never holds one, and the screens that save
 * and open files are written against nothing Android-only but the call to one of these.
 *
 * Guarded like [launchExternally]: an OEM build can ship without a picker, or the tutor can switch it off,
 * and `launch` then throws the same `ActivityNotFoundException` a tap would crash on. It says
 * [failureMessage] in the screen's snackbar instead.
 */

/**
 * Asks where to save a new document of [mimeType]; call the result with the name to suggest. [onCreated]
 * hears only of a document actually created — backing out of the picker answers nothing.
 */
@Composable
fun rememberCreateDocument(
    mimeType: String,
    failureMessage: String,
    messages: Messages,
    onCreated: (DocumentRef) -> Unit,
): (suggestedName: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onCreated(DocumentRef(it.toString())) }
    }
    return { name -> launcher.launchGuarded(name, failureMessage, messages) }
}

/**
 * Asks for an existing document; call the result with the MIME types to offer. [onOpened] hears only of a
 * document actually picked.
 */
@Composable
fun rememberOpenDocument(
    failureMessage: String,
    messages: Messages,
    onOpened: (DocumentRef) -> Unit,
): (mimeTypes: Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpened(DocumentRef(it.toString())) }
    }
    return { types -> launcher.launchGuarded(types, failureMessage, messages) }
}

private fun <I> ActivityResultLauncher<I>.launchGuarded(input: I, failureMessage: String, messages: Messages) {
    runCatching { launch(input) }.onFailure { messages.show(failureMessage) }
}
