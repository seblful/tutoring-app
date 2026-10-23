@file:Suppress("ForbiddenImport") // The wrapper the rule points features to.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one card that leads a screen in the accent colour — the next lesson on an overview. Tappable when
 * [onClick] is given; one per screen, or it stops leading.
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    }
}

/**
 * The action sheet over a screen: no drag handle of Material's, since each sheet draws its own (one a
 * screen reader can press to close).
 */
@Composable
fun KlasSheet(onDismiss: () -> Unit, state: SheetState, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, dragHandle = null, content = content)
}
