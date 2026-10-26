package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.sectionSurface

/**
 * A section **with its header inside the card**: the title (a heading for TalkBack), then a count or summary,
 * then at most one text action.
 */
@Composable
fun HeaderedCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    action: SectionAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier) {
        CardHeader(title, summary, action)
        Column(Modifier.padding(bottom = Spacing.inline), content = content)
    }
}

/**
 * The same header as the top slice of a section drawn **across list items** — a schedule day whose lessons
 * are lazy rows ([SurfaceRow] slices below it, on the same [sectionSurface]). [position] is [SlicePosition.FIRST]
 * when rows follow, [SlicePosition.ONLY] when the day has none to show.
 *
 * @param titleColor for a title that also marks something — the selected day, as [SectionTitle]'s colour does.
 */
@Composable
fun SliceHeader(
    title: String,
    position: SlicePosition,
    modifier: Modifier = Modifier,
    summary: String? = null,
    action: SectionAction? = null,
    titleColor: Color = Color.Unspecified,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(position.shapeOf(MaterialTheme.shapes.medium))
            .background(MaterialTheme.colorScheme.sectionSurface)
            .padding(bottom = if (position.roundsBottom) Spacing.inline else 0.dp),
    ) {
        CardHeader(title, summary, action, titleColor)
    }
}

@Composable
private fun CardHeader(
    title: String,
    summary: String?,
    action: SectionAction?,
    titleColor: Color = Color.Unspecified,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.gutter, end = Spacing.inline, top = Spacing.inline),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.inline)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                modifier = Modifier.semantics { heading() },
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.let { KlasButton(onClick = it.onClick, kind = ButtonKind.Text) { Text(it.label) } }
    }
}
