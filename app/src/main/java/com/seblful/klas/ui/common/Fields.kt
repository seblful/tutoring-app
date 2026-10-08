package com.seblful.klas.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.format.MoneyField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student

/**
 * The inline form fields: picking one of a few things, and the search box.
 *
 * Distinct from `Pickers.kt`, which holds the **modals** — a date, a time, a duration, a count. These are
 * what sits on the form itself.
 *
 * Two rules run through them. *Anything hidden behind a tap is drawn as a field* — a `Surface`, not a
 * disabled `OutlinedTextField` with its colours painted back in, which would cost the ripple. And *picking
 * one thing looks the same whether or not the thing has a row id*: [DropdownField] (a student, a group, a
 * subject) and [ChoiceField] (a fixed set of values) are the same private [MenuField].
 */

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
    // Closing the menu lets go of the field: a read-only box has nothing to type, and keeping the focus
    // left it outlined in the accent colour after the choice — the one «active» field on a form whose
    // [PickerField]s, the same kind of input beside it, never look that way.
    val focusManager = LocalFocusManager.current
    val close = {
        expanded = false
        focusManager.clearFocus()
    }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { wanted ->
            if (enabled) {
                if (wanted) expanded = true else close()
            }
        },
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
        ExposedDropdownMenu(expanded = open, onDismissRequest = close) {
            items(close)
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
    val contentAlpha = if (enabled) 1f else DisabledAlpha
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
/**
 * A money amount typed in full — a rate, a payment, a default — with the line under it that says what is
 * wrong with it.
 *
 * Seven fields spelled this out: the text, the red outline, the reason under it, one line, a decimal
 * keyboard. The red outline used to be the whole message; [MoneyField] knows which rule failed and this
 * is it saying so, the same way on every form and dialog.
 *
 * Takes the typed text rather than the next [MoneyField], because the two kinds of owner turn a keystroke
 * into a field differently: a form's lives in its view model, a dialog's is `rememberSaveable` text with
 * the field derived from it — so a half-typed amount survives the dialog being recreated.
 *
 * @param hint the field's ordinary supporting line; an error wins over it ([supportingTextOf]).
 */
@Composable
fun MoneyTextField(
    field: MoneyField,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = field.text,
        onValueChange = onValueChange,
        label = { Text(label) },
        // Red only for something unusable that was actually typed — `MoneyField.isError`'s rule. Blank is
        // incomplete, which a hint says without shouting.
        isError = field.isError,
        supportingText = supportingTextOf(error = moneyErrorText(field), hint = hint),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}
/**
 * Who a student is and how to reach them — name, phone, email, note, and where they are usually taught —
 * asked the same way by the add form and by the card's edit dialog.
 *
 * Written twice before this, and the copies had already parted: the form said a blank name was required
 * and the dialog only greyed its «Сохранить», leaving the tutor to guess which of five fields it meant.
 * Said as a **hint, not an error**: a blank name on a form that has just opened is incomplete rather than
 * wrong.
 *
 * Each field's keyboard says what goes in it, and «Далее» walks down the fields: a name capitalised as
 * one, a note as sentences. Emitted as siblings, so they take the caller's spacing.
 */
@Composable
fun StudentContactFields(
    name: String,
    onName: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    mode: ClassMode,
    onMode: (ClassMode) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.student_name_label)) },
        supportingText = supportingTextOf(
            hint = stringResource(R.string.field_required).takeIf { name.isBlank() },
        ),
        singleLine = true,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = phone,
        onValueChange = onPhone,
        label = { Text(stringResource(R.string.student_phone_label)) },
        singleLine = true,
        keyboardOptions = PhoneKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        label = { Text(stringResource(R.string.student_email_label)) },
        singleLine = true,
        keyboardOptions = EmailKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = onNote,
        label = { Text(stringResource(R.string.student_note_label)) },
        keyboardOptions = NoteKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )
    ModeField(
        label = stringResource(R.string.student_mode_label),
        selected = mode,
        onSelect = onMode,
    )
}
private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
private val PhoneKeyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
private val EmailKeyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
private val NoteKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
/**
 * A **shortlist with an escape hatch**, as chips: one per preset, then [customLabel], which asks for
 * anything else.
 *
 * The last chip is lit whenever [selected] is not one of the presets — otherwise a lesson booked at 50
 * minutes, or a run of six, would show no chip selected and read as nothing having been set. Two pickers
 * on the booking form follow that rule, and each had its own copy of it.
 *
 * @param selected the value in force, or null while there is none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PresetChips(
    presets: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    customLabel: String,
    onSelect: (T) -> Unit,
    onCustom: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(label(preset)) },
            )
        }
        FilterChip(
            selected = selected != null && selected !in presets,
            onClick = onCustom,
            label = { Text(customLabel) },
        )
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
 *
 * **The tick is felt**, and here rather than at the two call sites for the reason the row itself is shared:
 * this is one act, and it changes a stored fact — who is on a roster, who was at a lesson. That is what
 * earns a haptic, whereas the row being *tappable* would not; and because the whole row is the target, a
 * thumb covers the checkbox it just changed, so the buzz is often the only immediate answer.
 */
@Composable
fun MemberPickRow(
    student: Student,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleFeedback = rememberToggleFeedback()
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { on -> toggleFeedback(on); onToggle(on) })
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
