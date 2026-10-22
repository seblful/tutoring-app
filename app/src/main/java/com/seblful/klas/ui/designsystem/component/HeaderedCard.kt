package com.seblful.klas.ui.designsystem.component

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing

/**
 * A section **with its header inside the card**: the title (a heading for TalkBack), then a count or summary,
 * then at most one text action. This replaces headers drawn above cards in some places and titles without
 * counts in others.
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
        Column(Modifier.padding(bottom = Spacing.inline), content = content)
    }
}
