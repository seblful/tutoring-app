package com.seblful.klas.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import com.seblful.klas.lock.LocalAppLocked
import com.seblful.klas.lock.UnlockedWindow
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/*
 * The inline form fields: picking one of a few things, and the search box.
 *
 * Distinct from `Pickers.kt`, which holds the **modals** — a date, a time, a duration, a count. These are
 * what sits on the form itself.
 *
 * Two rules run through them. *Anything hidden behind a tap is drawn as a field* — the outlined field's own
 * decoration around a tappable box, not a disabled `OutlinedTextField` with its colours painted back in,
 * which would cost the ripple. And *picking
 * one thing looks the same whether or not the thing has a row id*: [DropdownField] (a student, a group, a
 * subject) and [ChoiceField] (a fixed set of values) are the same private [MenuField].
 */

/**
 * Where a class is held, chosen the way every other single answer on a form is: a [ChoiceField].
 *
 * @param label «Место» on a lesson, «Место занятий» where the default for future lessons is being set —
 * the same control answering two different questions.
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
 * row-backed thing (a student, a group, a subject) by id, ticking [selectedId] among them.
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
    selectedId: Long? = null,
) {
    MenuField(
        label = label,
        selectedText = selectedText,
        enabled = enabled,
        supportingText = supportingText,
        isError = isError,
    ) { dismiss ->
        MenuOptions(options, nameOf = { it.second }) { (id, name) ->
            TickedMenuItem(
                text = name,
                selected = id == selectedId,
                onClick = { onSelect(id); dismiss() },
            )
        }
    }
}
/**
 * The same field, for picking one of a **fixed set of values** rather than one of a table's rows — a
 * class mode today, any other small enum a form has to ask about tomorrow.
 *
 * @param optionLabel how a value reads to the tutor — `@Composable` because those labels are
 * `stringResource`s.
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
        // Labelled once the menu is open, so a long list can be searched by what it reads.
        val labelled = options.map { it to optionLabel(it) }
        MenuOptions(labelled, nameOf = { it.second }) { (option, text) ->
            TickedMenuItem(
                text = text,
                selected = option == selected,
                onClick = { onSelect(option); dismiss() },
            )
        }
    }
}
/**
 * The shape both dropdowns share: a bordered, read-only field showing what is chosen, opening a menu of
 * [items] on tap.
 *
 * @param items the menu's contents, given the [dismiss] that closes it — a menu item that selects
 * without closing would leave the tutor tapping outside to get rid of it.
 * @param supportingText what qualifies the choice, in the field's own slot, so it is spaced and indented
 * like the neighbouring fields' rather than as a standalone `Text` under the picker.
 * @param isError whether that supporting line is a **refusal** rather than a note. It has to be
 * separable: the booking form's subject picker says either «Предмет задан группой» (a fact) or «У
 * ученика нет предметов с ценой» (the reason nothing can be saved), and folding those two into one
 * grey line is how the second one stopped being visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuField(
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
        UnlockedWindow { drafts ->
            ExposedDropdownMenu(
                expanded = open,
                onDismissRequest = close,
                shape = MenuShape,
                containerColor = MenuContainerColor,
                shadowElevation = MenuShadowElevation,
            ) {
                drafts.SaveableStateProvider("body") { items(close) }
            }
        }
    }
}
/**
 * A field that is not typed into but **tapped** to open a picker — a date, a time.
 *
 * Drawn by the outlined text field's own decoration, so it is the same field as the dropdowns and the
 * money input beside it: the label sits inside while there is no value and floats into the border once
 * there is one, so two kinds of field never sit a few dp apart on a form spaced unlike each other. It
 * never takes focus — a picker opens instead — so it never wears the accent outline of a field being
 * typed into.
 */
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    // The room an outlined field keeps above its border for the floated label (`OutlinedTextField` pads
    // itself the same way), so a column of mixed fields spaced evenly gets even gaps.
    val labelRoom = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() / 2 }
    Column(modifier.fillMaxWidth().padding(top = labelRoom)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = OutlinedTextFieldDefaults.MinHeight)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            PressInsideBorder(interactions)
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) colors.unfocusedTextColor else colors.disabledTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
                isError = isError,
                label = { Text(label) },
                colors = colors,
            )
        }
        // Outside the tappable box, where an `OutlinedTextField` puts its own supporting line, so the press
        // ripple stays on the field rather than spreading over the sentence under it.
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    klasSemantics.validation
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
 * The focus a **reveal owes the tap that caused it**: an input that appears because something was
 * tapped takes the caret and the keyboard as it arrives, so one tap does what one tap looked like it
 * would.
 */
@Composable
fun rememberRevealedFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    val locked = LocalAppLocked.current
    LaunchedEffect(locked) { if (!locked) requester.requestFocus() }
    return requester
}
/**
 * A money amount typed in full — a rate, a payment, a default — with the line under it that says what is
 * wrong with it.
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
 * A [PickerField]'s press inside its rounded border, on a layer of its own: clipping the field itself would cut
 * the floated label, which sits across its top edge.
 */
@Composable
private fun BoxScope.PressInsideBorder(interactions: MutableInteractionSource) {
    Box(
        Modifier
            .matchParentSize()
            .clip(OutlinedTextFieldDefaults.shape)
            .indication(interactions, ripple()),
    )
}

/** One tappable person in a roster picker — a group's members, or who was at a lesson. */
@Composable
fun MemberPickRow(
    student: Student,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleFeedback = rememberToggleFeedback()
    // Material's checkbox list row, the tick leading as every selection control does (R6): it says it is
    // chosen, and the row adds no tint of its own. It reaches into the form's padding by the list item's
    // extra inset, so the tick keeps the form's edge.
    ListItem(
        headlineContent = { Text(student.name) },
        leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
        trailingContent = { Avatar(student.name, student.id) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .bleed(Spacing.gutter - Spacing.inline)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { on ->
                toggleFeedback(on); onToggle(on)
            }),
    )
}

/**
 * The search field the list screens carry in place of their title while searching: Material's search bar
 * input on its own rounded container. Not `SearchBar` itself, whose 360 dp minimum does not fit a phone's
 * title beside the bar's icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // Opening search and then having to tap the box would make the icon a two-tap affordance.
    val focusRequester = rememberRevealedFocus()
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            // The list filters as it is typed, so the keyboard's search key only puts the keyboard away.
            onSearch = { keyboard?.hide() },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(KlasIcons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(KlasIcons.Filled.Close, stringResource(R.string.search_clear))
                    }
                }
            },
        )
    }
}
