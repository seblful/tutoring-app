package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.people.AvatarPalette
import com.seblful.klas.ui.theme.klasColors

/*
 * Monograms: one student's.
 *
 * The colour keys off the student's **id** rather than their name (`domain/people/AvatarPalette`), so a rename does
 * not move it. A monogram is a *picture* and says nothing to a screen reader — every caller puts the full
 * name in the same row, so [Avatar] clears its own semantics.
 */

/**
 * A round monogram from the first letter of [name] — a lightweight student avatar. The colour comes
 * from [studentId] rather than the name, so the list reads as a set of distinct people rather than
 * a wall of identical chips, and a student keeps their colour when their name is corrected.
 */
@Composable
fun Avatar(name: String, studentId: Long, modifier: Modifier = Modifier) {
    val (container, content) = avatarColors(studentId)
    Box(
        modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = monogram(name), style = MaterialTheme.typography.titleMedium, color = content)
    }
}

private val AvatarSize = 40.dp

/** The first character of [name], in capitals — a whole code point, so an emoji or a rare ideograph is not halved. */
fun monogram(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val length = if (trimmed[0].isHighSurrogate() && trimmed.length > 1 && trimmed[1].isLowSurrogate()) 2 else 1
    return trimmed.substring(0, length).uppercase()
}

/**
 * The 40dp disc a top-level list row leads with, holding a glyph rather than a monogram — a group, a
 * subject.
 *
 * @param selected for a row tinted as selected, whose `secondaryContainer` the usual disc would vanish into.
 */
@Composable
fun IconAvatar(icon: ImageVector, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(if (selected) colors.surfaceContainerLowest else colors.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.onSurfaceVariant else colors.onSecondaryContainer,
            modifier = Modifier.size(AvatarGlyph),
        )
    }
}

private val AvatarGlyph = 22.dp

/** A student's avatar colours, disc then letter — also what their lessons are drawn in on a timeline. */
@Composable
internal fun avatarColors(studentId: Long): Pair<Color, Color> {
    val base = klasColors.harmonized(AvatarHues[AvatarPalette.indexFor(studentId, AvatarHues.size)])
    return if (klasColors.isDark) {
        base.copy(alpha = 0.35f) to lerp(base, Color.White, 0.55f)
    } else {
        base.copy(alpha = 0.22f) to lerp(base, Color.Black, 0.5f)
    }
}

private val AvatarHues = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF5350), // red
    Color(0xFFAB47BC), // purple
    Color(0xFF42A5F5), // blue
    Color(0xFF66BB6A), // green
    Color(0xFFFFA726), // orange
    Color(0xFFEC407A), // pink
    Color(0xFF8D6E63), // brown
    Color(0xFF78909C), // blue grey
)
