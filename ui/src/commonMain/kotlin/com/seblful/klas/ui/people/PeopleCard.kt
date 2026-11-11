package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.designsystem.component.SurfaceRow

/**
 * A row on either side of «Люди», so switching tabs changes the subject and not the layout: an avatar, the
 * name with what needs attention at its end, and one line of facts. Rows share one surface ([SurfaceRow], D3).
 *
 * @param supporting the lesson first: the line is cut at its end, and when they are next seen is what the list
 * is read for — the subjects and the head count can be cut, the time cannot.
 * @param selected whether the row is the one open beside the list.
 * @param position where the row sits on the list's one surface; [SlicePosition.ONLY] stands alone.
 * @param trailing on the name's line, baseline to baseline — a balance, or the archived badge.
 */
@Composable
internal fun PeopleCard(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    position: SlicePosition = SlicePosition.ONLY,
    trailing: @Composable () -> Unit = {},
) {
    SurfaceRow(
        position = position,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        // Under the text, past the avatar — the row is the person, the avatar its mark.
        dividerInset = Spacing.gutter + AvatarSize + Spacing.leading,
    ) {
        // Roomier than a lesson card: a list of people is scanned by name, and two tight lines read as cramped.
        Row(Modifier.padding(vertical = Spacing.tight), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(Spacing.leading))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LineGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline().weight(1f),
                    )
                    // Capped: a `Row` measures this before the weighted name, so an amount at a large font
                    // scale would otherwise take the name's room.
                    Box(Modifier.alignByBaseline().widthIn(max = TrailingWidth)) { trailing() }
                }
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    // Paired with the card's own fill: the selected one is a container with its own ink.
                    color = MaterialTheme.colorScheme.let {
                        if (selected) it.onSecondaryContainer else it.onSurfaceVariant
                    },
                    // Two: on one, a group's member count — the end of its line — was what got cut.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val TrailingWidth = 160.dp
private val AvatarSize = 40.dp
private val LineGap = 2.dp
