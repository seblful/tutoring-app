package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.seblful.klas.ui.common.DisabledAlpha
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberToggleFeedback

/**
 * What leads a row — **one of these**, or nothing: typed, so a row cannot lead with a control of its own
 * making, as Today's to-mark rows once did with a circle beside the [Done] button every other list has.
 */
sealed interface RowStart {
    /** A navigation or setting row's icon, in the supporting tone unless [tint] says otherwise. */
    data class Icon(val icon: ImageVector, val tint: Color = Color.Unspecified) : RowStart

    /** The mark-done button ([DoneButton]): Today's lessons, the widget's tick in the app. */
    data class Done(
        val done: Boolean,
        val label: String,
        val doneWord: String,
        val onDone: () -> Unit,
        val onOpen: () -> Unit,
        val enabled: Boolean = true,
    ) : RowStart
}

/**
 * What trails a row — **exactly one** of these. A row cannot hold two trailing icons, or a bin beside an
 * amount, because the type has one slot.
 */
sealed interface RowEnd {
    data class Money(val amount: AmountText) : RowEnd

    data class Value(val text: String) : RowEnd

    /** The row opens a screen of the app. */
    data object Opens : RowEnd

    /** The row **is** the switch: a tap anywhere toggles it, and it is announced as one switch. */
    data class Toggle(val on: Boolean, val onChange: (Boolean) -> Unit) : RowEnd

    /** One icon action, named for TalkBack by [label]. */
    data class Action(val icon: ImageVector, val label: String, val onClick: () -> Unit) : RowEnd
}

/**
 * One row for every list: a title, an optional supporting line, at most one thing leading and one trailing.
 * Transparent, so it sits on whichever card holds it; the press is inset into the card like every row's.
 *
 * @param onClickLabel what a tap does, for TalkBack's "double-tap to …" — say it for rows that open things.
 * @param enabled dims the whole row and refuses taps while it cannot be used.
 */
@Composable
fun KlasRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    start: RowStart? = null,
    end: RowEnd? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
) {
    val toggle = end as? RowEnd.Toggle
    val toggleFeedback = rememberToggleFeedback()
    val press = when {
        toggle != null -> Modifier.insetRowPress { source ->
            Modifier.toggleable(
                value = toggle.on,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { on ->
                toggleFeedback(on)
                toggle.onChange(on)
            }
        }
        onClick != null -> Modifier.insetRowPress { source ->
            Modifier.clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
        }
        else -> Modifier
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = start?.let { { Lead(it) } },
        trailingContent = end?.let { { Trail(it, enabled) } },
        colors = rowColors(enabled),
        modifier = modifier.then(press),
    )
}

@Composable
private fun Lead(start: RowStart) {
    when (start) {
        is RowStart.Icon -> Icon(start.icon, contentDescription = null, tint = start.tint.takeOrElse())
        is RowStart.Done ->
            DoneButton(start.done, start.label, start.doneWord, start.onDone, start.onOpen, enabled = start.enabled)
    }
}

@Composable
private fun Trail(end: RowEnd, enabled: Boolean) {
    when (end) {
        is RowEnd.Money -> Amount(end.amount)
        is RowEnd.Value -> Text(end.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        RowEnd.Opens -> OpensScreenMark()
        // A picture of the state: the row is the control (see KlasRow).
        is RowEnd.Toggle -> Switch(checked = end.on, onCheckedChange = null, enabled = enabled)
        is RowEnd.Action -> IconButton(onClick = end.onClick, enabled = enabled) {
            Icon(end.icon, contentDescription = end.label)
        }
    }
}

@Composable
private fun Color.takeOrElse(): Color =
    if (this == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else this

@Composable
private fun rowColors(enabled: Boolean) = if (enabled) {
    ListItemDefaults.colors(containerColor = Color.Transparent)
} else {
    ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
        leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
        trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
    )
}
