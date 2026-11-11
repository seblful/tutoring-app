package com.seblful.klas.ui.designsystem.template

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.designsystem.component.FormBar
import com.seblful.klas.ui.designsystem.component.FormSaveBar

/**
 * The full-screen form (D5) — booking, a student, a group: the [title], the fields, and at the foot the typed
 * [bar] beside the button that names the act ([saveLabel]), riding above the keyboard. Choose it, then fill it:
 * the shell, the keyboard and the bar are its, the fields the form's.
 *
 * @param onLeave the form's leave guard (`leaveGuard`), so Back asks before throwing typed work away.
 * @param content the fields, given the modifier that takes the height above the bar; they scroll themselves.
 */
@Composable
fun FormScreen(
    title: String,
    saveLabel: String,
    bar: FormBar,
    onSave: () -> Unit,
    onLeave: () -> Unit,
    messages: Messages? = null,
    content: @Composable (Modifier) -> Unit,
) {
    // The keyboard is an inset under edge-to-edge, not a resize: the shell takes it ahead of the fields' scroll,
    // so the last field and the bar both stay above it.
    KlasScreen(title = title, onBack = onLeave, messages = messages, imeAware = true) { modifier, bottomInset ->
        Column(modifier) {
            content(Modifier.weight(1f))
            FormSaveBar(saveLabel, bar, onSave, bottomInset)
        }
    }
}
