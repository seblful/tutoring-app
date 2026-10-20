package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.people.AvatarPalette
import com.seblful.klas.ui.theme.klasColors

/**
 * Monograms: one student's, and a whole roster's.
 *
 * The colour keys off the student's **id** rather than their name (`domain/people/AvatarPalette`), so a rename does
 * not move it. A monogram is a *picture* and says nothing to a screen reader — every caller puts the full
 * name in the same row, so [Avatar] clears its own semantics, and [AvatarStack] replaces its children with
 * one description naming every member, including the ones behind «+N»: the truncation is what a row's width
 * can afford, and a reader has no width.
 */

/**
 * A round monogram from the first letter of [name] — a lightweight student avatar. The colour comes
 * from [studentId] rather than the name, so the list reads as a set of distinct people rather than
 * a wall of identical chips, and a student keeps their colour when their name is corrected.
 */
@Composable
fun Avatar(name: String, studentId: Long, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val (container, content) = avatarColors(studentId)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            // Scaled with the circle, so a monogram in an [AvatarStack] is not a full-size letter
            // cropped by a smaller ring.
            style = if (size >= 40.dp) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = content,
        )
    }
}
/**
 * The 40dp disc a top-level list row leads with, holding a glyph rather than a monogram — a group, a
 * subject.
 */
@Composable
fun IconAvatar(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(AvatarGlyph),
        )
    }
}

private val AvatarGlyph = 22.dp

@Composable
private fun avatarColors(studentId: Long): Pair<Color, Color> {
    val base = AvatarHues[AvatarPalette.indexFor(studentId, AvatarHues.size)]
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
/**
 * A row of overlapping monograms — how a roster is shown where the *names* are not the point.
 *
 * @param names the members, in the order they should appear — **all** of them, however many are drawn.
 * @param ids their student ids — [Avatar] colours by id, so a member keeps their colour everywhere.
 * @param max how many faces to draw before collapsing the remainder into «+N».
 */
@Composable
fun AvatarStack(
    names: List<String>,
    ids: List<Long>,
    modifier: Modifier = Modifier,
    max: Int = 4,
    size: Dp = 28.dp,
) {
    val shown = names.take(max)
    val overflow = names.size - shown.size
    val roster = stringResource(R.string.roster_members, names.joinToString(", "))
    Row(
        // Cleared and replaced rather than added to: the monograms and the «+2» are the parts, and a
        // reader that got both the description and the parts would hear the group twice over.
        modifier.clearAndSetSemantics { contentDescription = roster },
        // Negative spacing is the overlap: each face tucks under the one before it.
        horizontalArrangement = Arrangement.spacedBy((-size / 3)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, name ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    // A ring in the page's colour is what keeps overlapping faces separable.
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(AvatarRing),
            ) {
                Avatar(name, ids.getOrElse(index) { index.toLong() }, size = size - 2.dp)
            }
        }
        if (overflow > 0) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(AvatarRing),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

private val AvatarRing = 1.dp
