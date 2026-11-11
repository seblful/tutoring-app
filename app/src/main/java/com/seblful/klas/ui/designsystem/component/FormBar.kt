package com.seblful.klas.ui.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasMotion
import com.seblful.klas.ui.theme.klasSemantics

/**
 * What a form's save bar says beside its button — a **named decision** the form makes, said the same way on
 * every form. The button is on exactly when the bar is [Ready].
 */
sealed interface FormBar {
    /** «Still needed», then each [fields] by name: what the tutor has yet to give. */
    data class Missing(val fields: List<String>) : FormBar

    /** Something in the way the fields alone would not show — a taken hour — in the validation colour. */
    data class Blocked(val reason: String) : FormBar

    /** What the save will make: [summary] and its [detail], «Sofia Martin / Maths 25 $/h · In person». */
    data class Ready(val summary: String = "", val detail: String = "") : FormBar
}

/**
 * **Every full-screen form saves from here** (D5): at the bottom, where the thumb is, beside what the save will
 * do — or, while it cannot, what is still in the way ([bar]). Dialogs keep their own buttons. [label] names the
 * act: «Book», «Add student», «Create group», or «Save» for an edit.
 *
 * Its surface runs under the system navigation bar; its row is padded by [bottomInset] to clear it, and a form
 * hosted with `imeAware` carries it above the keyboard.
 */
@Composable
fun FormSaveBar(
    label: String,
    bar: FormBar,
    onSave: () -> Unit,
    bottomInset: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottomInset)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.itemGap),
            horizontalArrangement = Arrangement.spacedBy(Spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One line or two as what it says changes; the bar grows and shrinks to it rather than jumping.
            Column(Modifier.weight(1f).animateContentSize(KlasMotion.defaultSpatial())) { BarWords(bar) }
            KlasButton(onClick = onSave, enabled = bar is FormBar.Ready) { Text(label) }
        }
    }
}

@Composable
private fun BarWords(bar: FormBar) {
    when (bar) {
        is FormBar.Missing -> TwoLines(stringResource(Strings.class_still_needed), bar.fields.joinToString())
        is FormBar.Blocked -> TwoLines(bar.reason, color = klasSemantics.validation)
        is FormBar.Ready -> if (bar.summary.isNotEmpty()) TwoLines(bar.summary, bar.detail)
    }
}

@Composable
private fun TwoLines(headline: String, supporting: String = "", color: Color = Color.Unspecified) {
    Text(
        headline,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (supporting.isNotEmpty()) {
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
