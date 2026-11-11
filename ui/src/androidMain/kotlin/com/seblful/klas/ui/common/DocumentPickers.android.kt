package com.seblful.klas.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.seblful.klas.storage.DocumentRef

@Composable
actual fun rememberCreateDocument(
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

@Composable
actual fun rememberOpenDocument(
    failureMessage: String,
    messages: Messages,
    onOpened: (DocumentRef) -> Unit,
): (mimeTypes: Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpened(DocumentRef(it.toString())) }
    }
    return { types -> launcher.launchGuarded(types, failureMessage, messages) }
}

@Composable
actual fun rememberOpenFolder(
    failureMessage: String,
    messages: Messages,
    onPicked: (DocumentRef) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onPicked(DocumentRef(it.toString())) }
    }
    return { launcher.launchGuarded(null, failureMessage, messages) }
}

private fun <I> ActivityResultLauncher<I>.launchGuarded(input: I, failureMessage: String, messages: Messages) {
    runCatching { launch(input) }.onFailure { messages.show(failureMessage) }
}
