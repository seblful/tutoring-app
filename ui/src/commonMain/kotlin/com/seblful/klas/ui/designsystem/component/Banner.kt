package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
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
    Box(
        modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.gutter, end = Spacing.inline, top = Spacing.inline, bottom = Spacing.inline),
    ) {
        BannerLayout(
            icon = {
                Icon(
                    if (kind == BannerKind.Blocking) KlasIcons.Outlined.Warning else KlasIcons.Outlined.Info,
                    contentDescription = null,
                    tint = ink,
                )
            },
            text = {
                Column(Modifier.padding(vertical = Spacing.tight)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = ink)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ink) }
                }
            },
            action = action?.let { { BannerAction(it, ink) } },
        )
    }
}

/**
 * The icon, the words, and the action beside them — or, when the action would leave the words less than
 * [MinTextShare] of the line, under them at the end, as Material's banner does. A long label («Сохранить
 * копию») beside them folded the words into a column of one or two each.
 */
@Composable
private fun BannerLayout(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
) {
    Layout(listOfNotNull(icon, text, action), Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = Spacing.inline.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val iconPlaced = measurables[0].single().measure(loose)
        val actionPlaced = measurables.getOrNull(2)?.single()?.measure(loose)
        val textStart = iconPlaced.width + gap
        val room = width - textStart
        val besideRoom = room - (actionPlaced?.let { it.width + gap } ?: 0)
        val beside = actionPlaced == null || besideRoom >= room * MinTextShare
        val wordsWidth = (if (beside) besideRoom else room).coerceAtLeast(0)
        val words = measurables[1].single().measure(loose.copy(maxWidth = wordsWidth))
        val line = maxOf(iconPlaced.height, words.height, if (beside) actionPlaced?.height ?: 0 else 0)
        val height = line + if (beside) 0 else actionPlaced?.height ?: 0
        layout(width, height) {
            iconPlaced.place(0, (line - iconPlaced.height) / 2)
            words.place(textStart, (line - words.height) / 2)
            actionPlaced?.place(width - actionPlaced.width, if (beside) (line - actionPlaced.height) / 2 else line)
        }
    }
}

/** The least of the line the words keep with the action beside them. */
private const val MinTextShare = 0.6f

@Composable
private fun BannerAction(action: SectionAction, ink: Color) {
    KlasButton(onClick = action.onClick, kind = ButtonKind.Quiet) { Text(action.label, color = ink) }
}
