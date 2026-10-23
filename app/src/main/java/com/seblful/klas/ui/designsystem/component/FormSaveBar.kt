package com.seblful.klas.ui.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasMotion

/**
 * **Every full-screen form saves from here** (D5): at the bottom, where the thumb is, beside what the save will
 * do — or, while it cannot, what is still in the way ([status]). Dialogs keep their own buttons.
 *
 * Its surface runs under the system navigation bar; its row is padded by [bottomInset] to clear it, and a form
 * hosted with `imeAware` carries it above the keyboard.
 */
@Composable
fun FormSaveBar(
    label: String,
    enabled: Boolean,
    onSave: () -> Unit,
    bottomInset: PaddingValues,
    modifier: Modifier = Modifier,
    status: @Composable ColumnScope.() -> Unit = {},
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
            Column(Modifier.weight(1f).animateContentSize(KlasMotion.defaultSpatial()), content = status)
            KlasButton(onClick = onSave, enabled = enabled) { Text(label) }
        }
    }
}
