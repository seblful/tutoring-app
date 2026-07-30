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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.theme.klasColors

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
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = CardInsetY),
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
            .padding(horizontal = Spacing.inline, vertical = Spacing.tight),
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
 * @param compact for an empty state **inside** something — a detail card's tab — rather than one owning
 *   the screen. Smaller icon, no `fillMaxSize`, a quieter line, an outlined action.
 *
 *   The tabs used to draw a bare grey `Text` instead, through a private `TabEmptyState` that was
 *   defined **twice, identically**, in the two card files. So a tutor met a designed empty state on five
 *   screens and an unstyled sentence on five tabs — and the sentence had nowhere to put an action, which
 *   is the part of this component that matters.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            // A compact one is a block inside a list, so it takes the height it needs. A full one owns
            // the screen and centres in it.
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .padding(if (compact) Spacing.sectionGap else EmptyStateInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) CompactIconSize else EmptyStateIconSize),
                // Faint: it is scenery for the sentence, not a thing to look at.
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Text(
            text = title,
            // A tab's empty state sits under a heading that already says what the tab is, so its own
            // line is a statement rather than a headline.
            style = if (compact) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (compact) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
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
            // Outlined inside a tab: a filled button is the primary action *of a screen*, and a tab's
            // way out is not competing with the card's own pencil for that role.
            if (compact) {
                OutlinedButton(onClick = onAction) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(actionLabel, Modifier.padding(start = Spacing.inline))
                }
            } else {
                Button(onClick = onAction, modifier = Modifier.padding(top = Spacing.tight)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(actionLabel, Modifier.padding(start = Spacing.inline))
                }
            }
        }
    }
}

/**
 * The one way to add to a tab's list, sitting at its top where a section header's action used to.
 *
 * Held to the [MinTouchTarget] floor: a bare `TextButton` measures to its text, which on these rows
 * came out around 36 dp — under the minimum, on the only control that adds anything to the tab.
 *
 * In `ui/common/` rather than private to the student's card, because it was private to the student's
 * card and that is what made the two cards disagree: a student's subjects tab offered «Добавить
 * предмет» and the group's roster tab named its emptiness and offered nothing. The group's way out
 * turned out to belong in its **empty state** instead (a roster is changed on the form, not appended to
 * from here), so both callers are still the student's — but the next tab that needs a top-of-list add
 * no longer has to reach into another screen's file for one.
 */
@Composable
fun AddRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(start = Spacing.inline).heightIn(min = MinTouchTarget),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(label, Modifier.padding(start = Spacing.inline))
    }
}

private val EmptyStateIconSize = 56.dp

/** Inside a tab there is a card's worth of chrome above already, so the glyph is scenery at half size. */
private val CompactIconSize = 32.dp

/**
 * An empty state is deliberately inset further than the app's gutter: it is one centred sentence on an
 * otherwise blank screen, and the extra margin is what stops the line running the full width.
 */
private val EmptyStateInset = 32.dp

/**
 * A [ContentCard]'s own vertical inset, a little tighter than [Spacing.gutter] because a card's text
 * already carries line spacing above and below it — 16 all round measures as too airy on a list row.
 */
private val CardInsetY = 12.dp

/**
 * What a money field says when what was typed will not do — the wording for [MoneyField.Error].
 *
 * The classification is `MoneyField`'s and is unit-tested; only the sentence is here. Returns null when
 * there is nothing wrong, which is exactly what an `OutlinedTextField`'s `supportingText` wants: use it
 * with [fieldSupport] so a field that is merely *required* still has something to say.
 */
@Composable
fun moneyErrorText(field: MoneyField): String? = field.error?.let { error ->
    stringResource(
        when (error) {
            MoneyField.Error.UNREADABLE -> R.string.money_error_unreadable
            MoneyField.Error.ZERO_PAYMENT -> R.string.money_error_zero_payment
        }
    )
}

/**
 * A field's supporting line: the reason it is **wrong** if it is, otherwise its ordinary hint.
 *
 * One slot, one line, and **the error wins** — that ordering is the whole point. A field that shows its
 * hint while red is telling the tutor how it works instead of what to fix.
 *
 * Returns null when there is nothing to say, so a field with neither reserves no line — which is why it
 * hands back the whole `supportingText` slot rather than a string.
 */
fun supportingTextOf(error: String? = null, hint: String? = null): (@Composable () -> Unit)? =
    (error ?: hint)?.let { text -> { Text(text) } }

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
fun classStatusLabel(status: LessonStatus): String = stringResource(
    when (status) {
        LessonStatus.SCHEDULED -> R.string.class_status_scheduled
        LessonStatus.COMPLETED -> R.string.class_status_completed
        LessonStatus.CANCELLED -> R.string.class_status_cancelled
    }
)

/**
 * The tint a status is said in: the brand colour for a lesson still to come, **green for one taught**,
 * **the theme's error red for a cancelled one** — the same red a debt is written in ([balanceColor]),
 * red meaning "look here" in both places.
 *
 * Taught was `tertiary`, this palette's teal, which read as a second blue beside the indigo of a lesson
 * still to come — the palette's `md_success` carries the whole argument. Green is what "done" is said in
 * outside the app, and it now says it everywhere inside: this badge and the panel the schedule's swipe
 * reveals are the two tones of the one colour.
 *
 * Cancelled was `onSurfaceVariant` before, which left grey saying two different things: "cancelled" and
 * the «Разные» badge a group lesson got when its members' statuses disagreed. That badge is gone — a
 * lesson now answers with one of these three ([ClassSession.status]) — so grey is nobody's status. The
 * lesson *card* still recedes when it is off — dimmed, its time struck through — so a cancellation stays
 * quiet in a list of lessons while remaining findable in it.
 */
@Composable
fun classStatusColor(status: LessonStatus): Color = when (status) {
    LessonStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    LessonStatus.COMPLETED -> klasColors.success
    LessonStatus.CANCELLED -> MaterialTheme.colorScheme.error
}

/** Localized label for a class mode: «Очно» / «Удалённо». */
@Composable
fun classModeLabel(mode: ClassMode): String = stringResource(
    when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }
)

/**
 * Where a class is held, chosen the way every other single answer on a form is: a [ChoiceField].
 *
 * It used to be a bare label above two filter chips. Two chips are a fine control in isolation, but on
 * these forms the mode sits among bordered fields — a student, a subject, a date — and was the only
 * input drawn without a border, so it read as a caption with buttons under it rather than as the fourth
 * field. On the booking form it was worse than inconsistent: folded it was a [PickerField] and tapping
 * it *replaced* that with the label-and-chips, so one input had two different appearances.
 *
 * @param label «Место» on a lesson, «Место занятий» where the default for future lessons is being set —
 *   the same control answering two different questions.
 */
@Composable
fun ModeField(
    label: String,
    selected: ClassMode,
    onSelect: (ClassMode) -> Unit,
) {
    ChoiceField(
        label = label,
        selected = selected,
        options = ClassMode.entries,
        optionLabel = { mode -> classModeLabel(mode) },
        onSelect = onSelect,
    )
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
            // Five controls is the tallest dialog in the app, so it scrolls: with the keyboard up — or at
            // a large font scale — «Место» and the note fell off the bottom with no way to reach them,
            // an `AlertDialog`'s text slot having no scroll of its own.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.inline),
            ) {
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
                ModeField(
                    label = stringResource(R.string.student_mode_label),
                    selected = mode,
                    onSelect = { mode = it },
                )
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
@Composable
fun DropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    MenuField(
        label = label,
        selectedText = selectedText,
        enabled = enabled,
        supportingText = supportingText,
        isError = isError,
    ) { dismiss ->
        options.forEach { (id, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = { onSelect(id); dismiss() },
            )
        }
    }
}

/**
 * The same field, for picking one of a **fixed set of values** rather than one of a table's rows — a
 * class mode today, any other small enum a form has to ask about tomorrow.
 *
 * Separate from [DropdownField] because the two answer differently shaped questions: an id-backed
 * choice can be selected without being in the list it offers (a student the form was opened on, before
 * their row has loaded), so its displayed text is passed in, while here [selected] is always one of
 * [options] and the label is derived. What they must never differ on is how they *look*, which is why
 * both are the same [MenuField].
 *
 * @param optionLabel how a value reads to the tutor — `@Composable` because those labels are
 *   `stringResource`s.
 */
@Composable
fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    MenuField(
        label = label,
        selectedText = optionLabel(selected),
        enabled = enabled,
        supportingText = supportingText,
        isError = isError,
    ) { dismiss ->
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(optionLabel(option)) },
                onClick = { onSelect(option); dismiss() },
            )
        }
    }
}

/**
 * The shape both dropdowns share: a bordered, read-only field showing what is chosen, opening a menu of
 * [items] on tap.
 *
 * Private on purpose. It exists so that "pick one thing" cannot end up looking like two different
 * controls depending on whether the thing has a row id — the mistake this file's [PickerField] KDoc
 * describes, made once already with price and place.
 *
 * @param items the menu's contents, given the [dismiss] that closes it — a menu item that selects
 *   without closing would leave the tutor tapping outside to get rid of it.
 * @param supportingText what qualifies the choice, in the field's own slot. The group form used to put
 *   this in a standalone `Text` under the subject picker while the rate field beside it used its own
 *   `supportingText` — 20 dp apart, indented differently, on the same form.
 * @param isError whether that supporting line is a **refusal** rather than a note. It has to be
 *   separable: the booking form's subject picker says either «Предмет задан группой» (a fact) or «У
 *   ученика нет предметов с ценой» (the reason nothing can be saved), and folding those two into one
 *   grey line is how the second one stopped being visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuField(
    label: String,
    selectedText: String,
    enabled: Boolean,
    supportingText: String? = null,
    isError: Boolean = false,
    items: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && enabled
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            supportingText = supportingText?.let { text -> { Text(text) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            items { expanded = false }
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
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val border = when {
        isError -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = PickerFieldHeight),
            shape = MaterialTheme.shapes.extraSmall,
            color = Color.Transparent,
            border = BorderStroke(1.dp, border),
        ) {
            Column(
                Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
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
        // Positioned and styled to match an `OutlinedTextField`'s own supporting line, so a form mixing
        // the two has one place a field explains itself rather than two that look different.
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.tight,
                ),
            )
        }
    }
}

/**
 * The hairline of page colour that keeps overlapping monograms separable in an [AvatarStack]. A stroke
 * width, not a gap, which is why it is not on the `Spacing` scale.
 */
private val AvatarRing = 1.dp

/** Matches an `OutlinedTextField`'s height, so a row mixing the two lines up. */
private val PickerFieldHeight = 56.dp

/**
 * The focus a **reveal owes the tap that caused it**: an input that appears because something was
 * tapped takes the caret and the keyboard as it arrives, so one tap does what one tap looked like it
 * would.
 *
 * Attach the returned requester with `Modifier.focusRequester(...)` to the field being revealed. The
 * focus is asked for on first composition, which for a field inside an `if` *is* the moment it appears.
 *
 * Shared because this is the same defect twice over. Search had it fixed inline — tapping the icon
 * offered a box that then had to be tapped again — and the booking form's price still had it: the first
 * tap swapped the [PickerField] for a text field and the second one put the cursor in it, for an input
 * whose whole purpose is to be typed into.
 */
@Composable
fun rememberRevealedFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    return requester
}

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
            .padding(horizontal = Spacing.inline, vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(student.name, student.id)
        Text(
            student.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = Spacing.leading),
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
    // Opening search and then having to tap the box would make the icon a two-tap affordance.
    val focusRequester = rememberRevealedFocus()
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
