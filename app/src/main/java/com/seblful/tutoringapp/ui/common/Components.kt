package com.seblful.tutoringapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money

/**
 * The widest a single column of content should grow. On phones (always narrower than this) it's a
 * no-op and content stays full-width; on tablets, unfolded foldables and landscape it caps the line
 * length so text fields and list rows stay readable instead of stretching edge-to-edge.
 */
val MaxContentWidth = 600.dp

/**
 * Caps content at [MaxContentWidth] and centres it horizontally on wide screens, while leaving
 * phones full-width. Apply to a screen's top-level scrollable content (a scrolling `Column`, a
 * `LazyColumn`, or a settings list). Pairs with `fillMaxSize()`/`fillMaxWidth()` — put this after
 * the size modifier and before content padding, e.g. `Modifier.fillMaxSize().constrainedContent()`.
 */
fun Modifier.constrainedContent(max: Dp = MaxContentWidth): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)

/**
 * A tappable surface for one item in a content list (schedule classes, students). Gives the main
 * lists a modern, card-based look; settings-style lists keep plain rows on purpose.
 */
@Composable
fun ContentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

/** A small tonal pill — e.g. a class status. The tint is derived from [color]. */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * A round monogram from the first letter of [name] — a lightweight student avatar. Each student
 * gets a stable colour derived from their name, so the list reads as a set of distinct people
 * rather than a wall of identical chips.
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier) {
    val (container, content) = avatarColors(name)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

/**
 * A stable (container, on-container) colour pair for [name], picked from a fixed palette by hashing
 * the name. Same name → same colour every time; the palette tints adapt to light/dark via alpha.
 */
@Composable
private fun avatarColors(name: String): Pair<Color, Color> {
    val palette = listOf(
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
    val key = name.trim().ifEmpty { "?" }
    val base = palette[(key.hashCode() % palette.size + palette.size) % palette.size]
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    // Tinted container with a strong, legible monogram in the same hue.
    return if (dark) base.copy(alpha = 0.35f) to base.copy(alpha = 0.95f)
    else base.copy(alpha = 0.22f) to base
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Human-readable balance line: debt, credit, or settled. */
@Composable
fun balanceDescription(balance: Money): String = when {
    balance.isZero -> stringResource(R.string.balance_settled)
    balance.isNegative -> stringResource(R.string.balance_credit, (Money.ZERO - balance).text())
    else -> stringResource(R.string.balance_owes, balance.text())
}

/** Colour for a balance: red for debt, theme primary for credit, muted when settled. */
@Composable
fun balanceColor(balance: Money): Color = when {
    balance.isZero -> MaterialTheme.colorScheme.onSurfaceVariant
    balance.isNegative -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

@Composable
fun classStatusLabel(status: ClassStatus): String = stringResource(
    when (status) {
        ClassStatus.SCHEDULED -> R.string.class_status_scheduled
        ClassStatus.COMPLETED -> R.string.class_status_completed
        ClassStatus.CANCELLED -> R.string.class_status_cancelled
    }
)

@Composable
fun classStatusColor(status: ClassStatus): Color = when (status) {
    ClassStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    ClassStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    ClassStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Localized label for a class mode: «Очно» / «Удалённо». */
@Composable
fun classModeLabel(mode: ClassMode): String = stringResource(
    when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }
)

/** A two-option chooser for a class mode (in person / remote). */
@Composable
fun ModeSelector(
    selected: ClassMode,
    onSelect: (ClassMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClassMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(classModeLabel(mode)) },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    text: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A dialog to edit a student's name, phone, email, optional note and default class mode. */
@Composable
fun StudentEditDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    initialNote: String,
    initialMode: ClassMode,
    onConfirm: (name: String, phone: String, email: String, note: String, mode: ClassMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var email by remember { mutableStateOf(initialEmail) }
    var note by remember { mutableStateOf(initialNote) }
    var mode by remember { mutableStateOf(initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.student_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.student_phone_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.student_email_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.student_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.student_mode_label), style = MaterialTheme.typography.labelLarge)
                ModeSelector(selected = mode, onSelect = { mode = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), phone.trim(), email.trim(), note.trim(), mode); onDismiss() },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A dialog with a single text field. Used for naming subjects, entering amounts, etc.
 *
 * @param keyboardType numeric for amounts, text otherwise.
 * @param validate gates the confirm button (in addition to non-blank). For amounts this prevents
 *   silently dismissing on an unparseable value — the action stays disabled until the input is valid.
 */
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> Boolean = { true },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value); onDismiss() },
                enabled = value.isNotBlank() && validate(value),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
