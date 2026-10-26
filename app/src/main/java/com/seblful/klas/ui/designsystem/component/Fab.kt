@file:Suppress("ForbiddenImport") // The wrapper the rule points features to.

package com.seblful.klas.ui.designsystem.component

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The screen's one add action, **labelled**: "Book", not a bare "+" that meant four things on four
 * tabs. It shrinks to its icon when [expanded] is false (a list scrolled away from its top) and at a font
 * scale of 1.5 and above, where the label would cover the content it adds to. Either way it is named for
 * TalkBack by [label], read once.
 */
@Composable
fun KlasFab(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val roomy = LocalDensity.current.fontScale < LargeFontScale
    ExtendedFloatingActionButton(
        text = { Text(label, Modifier.clearAndSetSemantics {}) },
        icon = { Icon(KlasIcons.Filled.Add, contentDescription = null) },
        onClick = onClick,
        expanded = expanded && roomy,
        modifier = modifier.semantics { contentDescription = label },
    )
}

private const val LargeFontScale = 1.5f
