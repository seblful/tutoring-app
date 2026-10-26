package com.seblful.klas.ui.common

import android.content.ClipData
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import kotlinx.coroutines.launch

/** Something the tutor asked to carry elsewhere as plain text — a number into a contact, homework into a note. */
fun interface ClipboardCopy {
    fun copy(label: String, text: String)
}

/**
 * Android 13+ confirms a copy with its own preview of the clip; before that nothing says it landed, so
 * the screen does.
 */
internal fun confirmsItself(sdk: Int): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU

internal suspend fun Clipboard.copyPlainText(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

@Composable
fun rememberClipboardCopy(messages: Messages): ClipboardCopy {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.clipboard_copied)
    return remember(clipboard, scope, messages, copied) {
        ClipboardCopy { label, text ->
            scope.launch {
                clipboard.copyPlainText(label, text)
                if (!confirmsItself(Build.VERSION.SDK_INT)) messages.show(copied)
            }
        }
    }
}
