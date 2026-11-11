package com.seblful.klas.ui.theme

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import com.seblful.klas.ui.common.Spacing

/**
 * Default [Style]s of the app's own foundation-built components; a component applies its entry first and
 * the caller's `style` after it, so the caller wins.
 *
 * Material components stay on `MaterialTheme` until Material supports Styles. A Style cannot read
 * `MaterialTheme` (material3 keeps its locals internal), so shapes and type come from this package's
 * [Shapes] and [Typography], the same instances [KlasTheme] hands Material.
 */
object KlasStyles {
    /** `StatusBadge`'s pill; its tint is the status colour, set per call. */
    val statusBadge: Style = Style {
        shape(Shapes.small)
        clip()
        contentPadding(horizontal = Spacing.inline, vertical = Spacing.tight)
    }
}
