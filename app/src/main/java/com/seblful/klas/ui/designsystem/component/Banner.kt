package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/**
 * A fact about the app that asks for the tutor, above what it concerns, with the one [action] that settles it
 * («Allow», «Back up»). The same on every screen it appears on (R2); said when it appears, as a polite live
 * region, since it arrives with the screen rather than from a tap.
 */
@Composable
fun Banner(
    title: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: SectionAction? = null,
) {
    val (container, ink) = when (kind) {
        BannerKind.Blocking -> klasSemantics.blocking to klasSemantics.onBlocking
        BannerKind.Advisory -> klasSemantics.advisory to klasSemantics.onAdvisory
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        modifier = modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.gutter, end = Spacing.inline, top = Spacing.inline, bottom = Spacing.inline),
    ) {
        Icon(
            if (kind == BannerKind.Blocking) KlasIcons.Outlined.Warning else KlasIcons.Outlined.Info,
            contentDescription = null,
            tint = ink,
        )
        Column(Modifier.weight(1f).padding(vertical = Spacing.tight)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ink) }
        }
        action?.let { BannerAction(it, ink) }
    }
}

@Composable
private fun BannerAction(action: SectionAction, ink: Color) {
    KlasButton(onClick = action.onClick, kind = ButtonKind.Quiet) { Text(action.label, color = ink) }
}
