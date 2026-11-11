@file:Suppress("ForbiddenImport") // The wrapper the rule points features to.

package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.seblful.klas.ui.common.destructiveOutlinedButtonBorder
import com.seblful.klas.ui.common.destructiveOutlinedButtonColors
import com.seblful.klas.ui.common.destructiveTextButtonColors

/**
 * The app's one button. [withIcon] tightens the leading padding for a button that starts with an icon, as
 * Material asks.
 */
@Composable
fun KlasButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    withIcon: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val padding = if (withIcon) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    val textPadding =
        if (withIcon) ButtonDefaults.TextButtonWithIconContentPadding else ButtonDefaults.TextButtonContentPadding
    when (kind) {
        ButtonKind.Primary -> Button(onClick, modifier, enabled, contentPadding = padding, content = content)
        ButtonKind.Tonal -> FilledTonalButton(onClick, modifier, enabled, contentPadding = padding, content = content)
        ButtonKind.Outlined -> OutlinedButton(onClick, modifier, enabled, contentPadding = padding, content = content)
        ButtonKind.Text -> TextButton(onClick, modifier, enabled, contentPadding = textPadding, content = content)
        ButtonKind.Quiet -> TextButton(
            onClick,
            modifier,
            enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = textPadding,
            content = content,
        )
        ButtonKind.Destructive -> TextButton(
            onClick,
            modifier,
            enabled,
            colors = destructiveTextButtonColors(),
            contentPadding = textPadding,
            content = content,
        )
        ButtonKind.DestructiveOutlined -> OutlinedButton(
            onClick,
            modifier,
            enabled,
            colors = destructiveOutlinedButtonColors(),
            border = destructiveOutlinedButtonBorder(),
            contentPadding = padding,
            content = content,
        )
    }
}

/** The line between groups of rows or fields, in the scheme's divider tone unless it marks something. */
@Composable
fun KlasDivider(modifier: Modifier = Modifier, color: Color = DividerDefaults.color) {
    HorizontalDivider(modifier, color = color)
}
