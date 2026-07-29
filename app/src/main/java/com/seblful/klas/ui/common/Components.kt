package com.seblful.klas.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.seblful.klas.R
import com.seblful.klas.domain.AvatarPalette
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student

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
 * A tappable surface for one **item** in a content list (a lesson, a student, a group). Gives the
 * main lists a modern, card-based look; settings-style lists keep plain rows on purpose.
 *
 * Drawn on `surfaceContainerLow` — the bottom rung of the tonal ladder (see `theme/Color.kt`) —
 * because an item and the *section* that holds items are different things and used to be the same
 * grey. Sections use [SectionCard], one step higher.
 *
 * Takes its tap through `Card`'s own `onClick` rather than a `clickable` modifier. A `clickable`
 * handed *to* the card sits outside the card's clip, so the press ripple was drawn as a hard-cornered
 * grey rectangle over the rounded corners — a grey frame flashing behind every lesson that was
 * tapped in the schedule. `CardPressFeedbackTest` keeps it that way.
 *
 * @param dimmed for an item whose content no longer counts — a cancelled lesson. It stays fully
 *   legible (it is still a record of something) but stops competing with the lessons that are on.
 */
@Composable
fun ContentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                .copy(alpha = if (dimmed) DimmedAlpha else 1f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

/**
 * A surface for a **section** — a group of rows read as one thing: the stat strip, a report's
 * breakdown. One rung above [ContentCard] on the tonal ladder, which is what gives a screen holding
 * both a legible hierarchy instead of one flat grey.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** How far a cancelled lesson recedes: still readable, no longer competing. */
private const val DimmedAlpha = 0.5f

/** The floor for anything tappable, per the Material accessibility guidance. */
val MinTouchTarget = 48.dp

/**
 * A small tonal pill — e.g. a class status. The tint is derived from [color].
 *
 * Kept deliberately tight (8dp of side padding, one line) because of where it sits: on a lesson card
 * it shares a row with the lesson's *name*, and every dp the pill takes is a dp the name loses. A
 * status is a two-word fact; the name is the thing being read.
 */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            // labelSmall, not labelMedium. The pill is the secondary half of its row and the name
            // beside it is the primary one, so when the two compete for a phone's width the pill is
            // what gives — a status read at 11sp is still read; a name cut short is not.
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

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
            .background(container),
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
 * A stable (container, on-container) colour pair for the student with [studentId], picked from a
 * fixed palette by [AvatarPalette]. Same student → same colour every time; the palette tints adapt
 * to light/dark via alpha.
 */
@Composable
private fun avatarColors(studentId: Long): Pair<Color, Color> {
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
    val base = palette[AvatarPalette.indexFor(studentId, palette.size)]
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    // Tinted container with a strong, legible monogram in the same hue.
    return if (dark) base.copy(alpha = 0.35f) to base.copy(alpha = 0.95f)
    else base.copy(alpha = 0.22f) to base
}

/**
 * What a list says when it has nothing in it.
 *
 * Three parts, and the third is the point: an **icon** so the space reads as designed rather than as
 * a failure to load, a **line** saying what would be here, and — where there is one obvious thing to
 * do about it — the **action itself**, as a real button. A tutor who has just skipped onboarding
 * meets several of these in a row; a screen that names a problem and then makes them go and find the
 * fix is the difference between an app that is empty and an app that looks broken.
 *
 * @param title the one-line statement of what is missing.
 * @param detail an optional second line, for when *why* it is empty is not obvious from the title.
 * @param icon a glyph for the thing that is missing. Decorative — the title carries the meaning.
 * @param actionLabel with [onAction], the primary way out of the empty state.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(EmptyStateIconSize),
                // Faint: it is scenery for the sentence, not a thing to look at.
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(actionLabel, Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val EmptyStateIconSize = 56.dp

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

/**
 * A **lesson's** status: the one its classes share, or «Разные» when they don't — a group whose
 * absentee was cancelled while the rest stayed completed.
 *
 * Nullable, because that is how a lesson answers the question (`ClassSession.commonStatus`), and three
 * surfaces ask it: the schedule's badge, a group's lesson list, and a calendar cell's tally. Each had
 * its own copy of the fallback, which is one place too many for a rule about what an unanswerable
 * status looks like.
 */
@Composable
fun sessionStatusLabel(status: ClassStatus?): String =
    status?.let { classStatusLabel(it) } ?: stringResource(R.string.class_status_mixed)

/** The tint for [sessionStatusLabel] — neutral for mixed, having no one status to be coloured by. */
@Composable
fun sessionStatusColor(status: ClassStatus?): Color =
    status?.let { classStatusColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant

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

/**
 * Dialog behaviour for every dialog the tutor **types into**: a tap outside no longer closes it.
 *
 * The full-screen forms answer the same problem by asking «Сохранить изменения?» on the way out (see
 * `ui/common/UnsavedChanges.kt`). A dialog does not need the question — it already shows «Отмена» and
 * «Сохранить», so leaving it is a deliberate tap — it only needs to stop being closed *by accident*.
 */
private val KeepTypedInput = DialogProperties(dismissOnClickOutside = false)

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
        properties = KeepTypedInput,
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
        // A stray tap beside the dialog must not throw away what was typed into it — the same rule
        // the full-screen forms follow with their «Сохранить изменения?» prompt, kept cheap here:
        // a dialog already has «Отмена», so the fix is to stop *accidental* dismissal rather than to
        // ask about a deliberate one. Back still closes it, as it closes any dialog.
        properties = KeepTypedInput,
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

/**
 * A read-only text field that opens a menu of [options] — the app's one way of picking a single
 * row-backed thing (a student, a group, a subject) by id.
 *
 * Shared rather than per-screen because the group form and the booking form must offer a subject the
 * same way; they disagree only on what the list contains and whether it can be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

/**
 * A field that is not typed into but **tapped** to open a picker — a date, a time, a price, a place.
 *
 * Shared rather than private to the booking form, because that form is where its absence showed:
 * price and location were drawn as bare clickable `Text`, sitting directly under five bordered
 * fields. Same kind of input, three presentations, and the two without a border read as labels
 * rather than as things to tap. One component is what makes a form look built rather than assembled.
 *
 * Built on [Surface] rather than on a disabled `OutlinedTextField` whose colours are painted back in.
 * That trick worked visually and cost the ripple: a tap on the most-tapped input in the app produced
 * no feedback at all, so a slow picker read as a tap that had missed.
 *
 * @param value what is currently chosen; empty renders [placeholder] in the muted style a text
 *   field's own placeholder uses, so an unfilled picker reads as unfilled rather than as blank.
 */
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val border = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = PickerFieldHeight),
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
            Text(
                value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                },
            )
        }
    }
}

/** Matches an `OutlinedTextField`'s height, so a row mixing the two lines up. */
private val PickerFieldHeight = 56.dp

/** Material's standard opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * A row of overlapping monograms — how a roster is shown where the *names* are not the point.
 *
 * A group of eight rendered as «Аня, Борис, Вера, …» is a paragraph inside a list row, and the list
 * stops being scannable at exactly the point a tutor has enough groups to need scanning. Faces
 * answer "which group is this" at a glance and take one line however many there are; the overflow
 * count carries the rest.
 *
 * @param names the members, in the order they should appear.
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
    Row(
        modifier,
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
                    .padding(1.dp),
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
                    .padding(1.dp),
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
 * One tappable person in a roster picker — a group's members, or who was at a lesson.
 *
 * Shared because the two are the same act and used to look nothing alike: the group form drew an
 * avatar, a tinted selection and rounded corners, while the booking form drew a bare name and a
 * checkbox. A tutor picking people should not be able to tell which screen they are on from the
 * shape of the row.
 *
 * The whole row is one toggle rather than a row containing a checkbox, so the target is the full
 * width and comfortably past the 48dp minimum — the checkbox itself is `onCheckedChange = null`,
 * which makes it a picture of the state instead of a second, smaller way to change it.
 */
@Composable
fun MemberPickRow(
    student: Student,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .toggleable(value = selected, onValueChange = onToggle)
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(student.name, student.id)
        Text(
            student.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

/**
 * The search field the list screens carry in place of their title while searching.
 *
 * One component for all three lists, because the alternative — each list growing its own — is how
 * three different search boxes happen. It is deliberately not a Material `SearchBar`: that component
 * expands into a full-screen results surface with its own history, which is the right shape for
 * searching *across* things and the wrong one for filtering a list that is already on screen.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // Opening search and then having to tap the box would make the icon a two-tap affordance.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.search_clear))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            // Transparent, so the field reads as the app bar's title rather than as a control
            // dropped into it.
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
