package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.format.bulletJoin

/**
 * Facts joined by bullets on one line when they fit, else each on a line of its own — never one fact broken
 * across two, as «In person» was when wrapped with the phone before it.
 */
@Composable
fun FactsLine(parts: List<String>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = parts.filter { it.isNotBlank() }
    val joined = bulletJoin(shown)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val fits = remember(joined, style, constraints.maxWidth) {
            measurer.measure(joined, style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        }
        if (fits) {
            Text(joined, style = style, color = color, maxLines = 1)
        } else {
            Column { shown.forEach { Text(it, style = style, color = color) } }
        }
    }
}

/**
 * Where a student or a group is taught, as the second line under a profile's name — with the archived badge
 * at its end, on both profiles alike.
 */
@Composable
fun PlaceLine(mode: ClassMode, archived: Boolean, modifier: Modifier = Modifier) {
    val place = @Composable {
        Text(
            classModeLabel(mode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (archived) {
        BadgeLine(badge = { ArchivedBadge() }, modifier = modifier, badgeFirst = false, text = place)
    } else {
        Box(modifier) { place() }
    }
}
