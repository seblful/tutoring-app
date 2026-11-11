package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.seblful.klas.ui.common.DisabledAlpha
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing

/**
 * One section of a settings page: its title, then its rows **on one card** — the surface every other
 * screen groups things on, so Settings reads as the same app.
 *
 * @param title null for a page's untitled lead section, or the index's categories.
 */
@Composable
fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        if (title != null) SectionHeader(title) else Spacer(Modifier.height(Spacing.itemGap))
        SectionCard {
            Column(Modifier.padding(vertical = Spacing.inline), content = content)
        }
    }
}

/** A row on a [SettingsGroup]'s card: no surface of its own, dimmed as a whole while it cannot be used. */
@Composable
internal fun settingsRowColors(enabled: Boolean = true): ListItemColors =
    if (enabled) {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    } else {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
        )
    }

@Composable
private fun SectionHeader(title: String) {
    // The app's section title, in ink like every other — said as a heading, so the long list can be walked
    // by section. Set in to the rows' text, not the card's edge.
    SectionTitle(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.sectionGap, bottom = Spacing.inline),
    )
}
