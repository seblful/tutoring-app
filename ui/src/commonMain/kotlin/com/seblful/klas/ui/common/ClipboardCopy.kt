package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import kotlinx.coroutines.launch

/** Something the tutor asked to carry elsewhere as plain text — a number into a contact, homework into a note. */
fun interface ClipboardCopy {
    fun copy(label: String, text: String)
}

@Composable
fun rememberClipboardCopy(messages: Messages): ClipboardCopy {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(Strings.clipboard_copied)
    return remember(clipboard, scope, messages, copied) {
        ClipboardCopy { label, text ->
            scope.launch {
                clipboard.copyPlainText(label, text)
                if (!clipboardConfirmsCopy) messages.show(copied)
            }
        }
    }
}
