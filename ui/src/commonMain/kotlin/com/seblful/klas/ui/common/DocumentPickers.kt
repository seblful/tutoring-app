package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import com.seblful.klas.storage.DocumentRef

/*
 * The system file picker, as a screen asks for it: a function to call with what the picker needs, and an
 * answer that is already a [DocumentRef]. The platform's own handle exists only inside these, so a
 * screen — like the view models behind it — never holds one, and the screens that save and open files
 * are written against nothing platform-specific but the call to one of these.
 *
 * Guarded: an Android build can ship without a picker, or the tutor can switch it off. Where the
 * platform cannot show one, [failureMessage] is said in the screen's snackbar instead.
 */

/**
 * Asks where to save a new document of [mimeType]; call the result with the name to suggest. [onCreated]
 * hears only of a document actually created — backing out of the picker answers nothing.
 */
@Composable
expect fun rememberCreateDocument(
    mimeType: String,
    failureMessage: String,
    messages: Messages,
    onCreated: (DocumentRef) -> Unit,
): (suggestedName: String) -> Unit

/**
 * Asks for an existing document; call the result with the MIME types to offer. [onOpened] hears only of a
 * document actually picked.
 */
@Composable
expect fun rememberOpenDocument(
    failureMessage: String,
    messages: Messages,
    onOpened: (DocumentRef) -> Unit,
): (mimeTypes: Array<String>) -> Unit

/**
 * Asks for a folder to keep writing into; [onPicked] hears only of a folder actually picked. The grant it
 * comes with can be kept across restarts — see `FolderGrants.keep`.
 */
@Composable
expect fun rememberOpenFolder(
    failureMessage: String,
    messages: Messages,
    onPicked: (DocumentRef) -> Unit,
): () -> Unit
