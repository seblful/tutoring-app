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
 *
 * **Silent to a screen reader.** The monogram is a picture of the name it stands beside, and a picture
 * that is read aloud stops being one: TalkBack announced «И» and then «Ирина Петрова» on every row of
 * the students list, so the letter arrived as a word of its own before the name it was drawn from. Every
 * caller puts the full name in the same row — a list item's headline, a pick row's label, and in an
 * [AvatarStack] the whole roster at once — so there is no case where clearing this loses a fact.
 *
 * `clearAndSetSemantics` rather than `contentDescription = null`, because the thing to suppress is the
 * `Text` inside: a null description on the circle would leave the letter itself still announced.
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
 *
 * The students list leads every card with a 40dp monogram; the groups list — the other side of the same
 * segmented control — led with a bare 24dp glyph, and so did the subjects list. Flipping «Ученики |
 * Группы» moved every name sideways and swapped a set of faces for a column of icons. A group or a subject
 * has no letter worth drawing (two groups of one subject start with the same one), so the disc carries
 * what the row *is*, in the brand's neutral container rather than a per-id hue: it is the same on every
 * row of its list, and a hue per row would claim a distinction that is not there.
 *
 * Silent to a screen reader for [Avatar]'s reason — the name is beside it.
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

/** The glyph inside an [IconAvatar] — about the ink a monogram's letter has in the same circle. */
private val AvatarGlyph = 22.dp

/**
 * A stable (container, on-container) colour pair for the student with [studentId], picked from a
 * fixed palette by [AvatarPalette]. Same student → same colour every time.
 *
 * A tinted container, and the letter **in the same hue, taken toward the page's ink** — darker on a light
 * page, paler on a dark one. The letter used to be the hue itself on a tint of it, which is the same
 * colour twice: orange on orange was 1.5 : 1 on a light card, and the best of the ten stopped at 3.7 in
 * the dark. Mixed this way the worst of them clears 4.5 in both. Light or dark is the theme's answer
 * ([klasColors]), not the phone's.
 */
@Composable
private fun avatarColors(studentId: Long): Pair<Color, Color> {
    val base = AvatarHues[AvatarPalette.indexFor(studentId, AvatarHues.size)]
    return if (klasColors.isDark) {
        base.copy(alpha = 0.35f) to lerp(base, Color.White, 0.55f)
    } else {
        base.copy(alpha = 0.22f) to lerp(base, Color.Black, 0.5f)
    }
}

/** The ten hues a student can be — once, rather than a list built on every avatar's composition. */
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
 * A group of eight rendered as «Аня, Борис, Вера, …» is a paragraph inside a list row, and the list
 * stops being scannable at exactly the point a tutor has enough groups to need scanning. Faces
 * answer "which group is this" at a glance and take one line however many there are; the overflow
 * count carries the rest.
 *
 * ### Spoken as a roster, not as letters
 *
 * The stack is drawn from single characters, so left alone it reached a screen reader as what it is made
 * of rather than as what it means: four disconnected letters and «+2», with nothing saying they were
 * people or that they belonged to the group whose row they sat in. One description replaces the lot.
 *
 * It names **every** member, including the ones the «+N» stands in for. The truncation is a concession to
 * the width of a list row and a reader does not have one — a tutor who cannot see the faces should not
 * also be the only one who cannot find out who is in the group. The rest of the app already works this
 * way; `DayCell` builds a whole spoken sentence for a calendar cell that draws three dots.
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
/**
 * The hairline of page colour that keeps overlapping monograms separable in an [AvatarStack]. A stroke
 * width, not a gap, which is why it is not on the `Spacing` scale.
 */
private val AvatarRing = 1.dp
