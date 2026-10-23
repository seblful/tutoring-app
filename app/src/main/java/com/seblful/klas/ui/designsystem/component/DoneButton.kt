@file:Suppress("ForbiddenImport") // The design system's own round button, as NamedIconButton is.

package com.seblful.klas.ui.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasColors

/**
 * The round mark-done button (R6) — the widget's tick, in the app. One-way: the empty ring marks the item done,
 * and the new state is then spoken ([doneWord]); done, it is a filled tick and a tap opens the item instead,
 * Undo being the message's. Off for an item that cannot be done yet, such as a lesson still to come. Never
 * beside a selection box.
 *
 * @param label what a tap does, per item — «Mark Olivia Brooks, 15:00 taught», never a bare «Mark».
 */
@Composable
fun DoneButton(
    done: Boolean,
    label: String,
    doneWord: String,
    onDone: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = if (done) onOpen else onDone,
        enabled = enabled,
        modifier = modifier.semantics {
            contentDescription = label
            if (done) stateDescription = doneWord
        },
    ) {
        Icon(
            if (done) KlasIcons.Filled.CheckCircle else KlasIcons.Outlined.Circle,
            contentDescription = null,
            tint = (if (done) klasColors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = if (enabled) 1f else DisabledAlpha),
        )
    }
}

/** Material's disabled content opacity, which an explicit tint would otherwise override. */
private const val DisabledAlpha = 0.38f
