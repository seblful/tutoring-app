package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.LoadingFormScreen
import com.seblful.klas.ui.common.MemberPickRow
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.RateHistoryList
import com.seblful.klas.ui.common.SaveAction
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.moneyErrorText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.supportingTextOf

/**
 * Create or edit one group: **subject**, name, note, hourly rate, default mode and roster.
 *
 * Subject comes before the roster because it decides it: pick one and the member list narrows to the
 * students priced for it, since a group only ever teaches that subject. Leaving it unset is allowed
 * only so that a group without one keeps working; those list every active student instead.
 *
 * **The form is a `LazyColumn`.** Every other form in the app is a `verticalScroll`, and rightly so —
 * they are a fixed handful of fields. This one ends in a list as long as the tutor's student list, and
 * a `forEach` inside a scroll composes and measures all of it: forty students meant forty
 * `MemberPickRow`s with forty avatars, none recycled, most off-screen, on every frame. The fields are
 * `item`s and the roster is `items`, which is the only part that grows.
 *
 * **What that costs, said out loud because it is a real cost.** A lazy item scrolled out of the viewport
 * is *disposed*, and the fields sit above a roster long enough to scroll them off — so scrolling down to
 * tick the fortieth member while «Название» holds the caret drops the focus and the keyboard, which a
 * `verticalScroll` would not have done. What it cannot do is lose or corrupt a value: every one lives in
 * `GroupFormViewModel`, not in the field, and text cannot arrive in a box that is not focused — reaching
 * one again means tapping it, which is also what places the caret. So the trade is a dropped keyboard on
 * a long scroll against composing the whole roster on every frame of it, made deliberately that way
 * round. **The fields must stay value-driven from the view model for that to hold**: a field keeping its
 * own `TextFieldValue` in a plain `remember` would lose the caret position here rather than the focus.
 */
@Composable
fun GroupFormScreen(
    onBack: () -> Unit,
    viewModel: GroupFormViewModel = hiltViewModel(),
) {
    val screenState by viewModel.state.collectAsState()
    val title = stringResource(
        if (viewModel.isNew) R.string.group_add_title else R.string.group_edit_title
    )

    // An existing group's form waits rather than drawing itself from a blank [GroupFormViewModel.Form]:
    // every field here is a stored value, and the blank version of this screen states that the group has
    // no name, no rate and — in the error red — that the tutor has no students. See [Loadable].
    when (val loaded = screenState) {
        Loadable.Loading -> LoadingFormScreen(title = title, onBack = onBack, fields = 5)
        is Loadable.Ready -> GroupForm(loaded.value, title, viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupForm(
    state: GroupFormViewModel.UiState,
    title: String,
    viewModel: GroupFormViewModel,
    onBack: () -> Unit,
) {
    val form = state.form

    // Back — the arrow or the system gesture — asks before throwing away edits to a group.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = { viewModel.save { onBack() } })

    val messages = rememberMessages()
    val notPricedMsg = stringResource(R.string.group_members_not_priced)
    ShowMessagesFor(viewModel.events, messages) { notPricedMsg }

    KlasScreen(
        title = title,
        onBack = requestLeave,
        messages = messages,
        // The rate field and the roster are at the bottom of a long form, and under edge-to-edge the
        // IME is an inset rather than a resize — the shell composes it in ahead of the scroll.
        imeAware = true,
        actions = { SaveAction(enabled = state.canSave, onSave = { viewModel.save { onBack() } }) },
    ) { modifier ->
        LazyColumn(
            modifier,
            // The list owns the gutter, as every list in the app does — see [Spacing].
            contentPadding = PaddingValues(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            item(key = "name") {
                // A name is required, and says so — as a **hint, not an error**. `canSave` knew it and
                // only greyed out the save button, which left the tutor to work out which of six fields
                // it meant; but a blank box on a form that has just opened is incomplete, not wrong, and
                // marking it red before it has been reached is the thing `MoneyField.error`'s KDoc
                // argues against.
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    supportingText = supportingTextOf(
                        hint = stringResource(R.string.field_required).takeIf { form.name.isBlank() },
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "subject") {
                // Subject first: it decides who may be in the group. 0 is the "not chosen" row — a real
                // option rather than a hidden state, because a group legitimately has none and the
                // tutor needs to see that rather than guess.
                //
                // Its hint is the field's own `supportingText`. It was a standalone `Text` 20 dp below,
                // indented differently from the rate field's hint directly underneath it — two hint
                // mechanisms, one after the other, on the same form.
                DropdownField(
                    label = stringResource(R.string.group_subject_label),
                    selectedText = state.subjects.firstOrNull { it.id == form.subjectId }?.name
                        ?: stringResource(R.string.group_subject_none),
                    options = listOf(0L to stringResource(R.string.group_subject_none)) +
                        state.subjects.map { it.id to it.name },
                    onSelect = { id -> viewModel.setSubject(id.takeIf { it != 0L }) },
                    supportingText = stringResource(
                        if (form.subjectId == null) R.string.group_subject_none_hint
                        else R.string.group_subject_hint
                    ),
                )
            }

            item(key = "note") {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                    label = { Text(stringResource(R.string.student_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "price") {
                // The group's own rate: what each member of its lessons is billed, whatever the
                // subject. Required — a group prices its own lessons, so it has to say what they cost —
                // and shown plainly rather than behind a tap, because it is the substance of the group,
                // not a detail.
                OutlinedTextField(
                    value = form.price.text,
                    onValueChange = { v -> viewModel.update { it.copy(price = it.price.typed(v)) } },
                    label = { Text(stringResource(R.string.group_price_label)) },
                    // Red only for something unusable that was actually typed — `MoneyField.isError`'s
                    // rule. Blank is required-and-unfilled, which the supporting line says without
                    // shouting.
                    isError = form.price.isError,
                    // The reason it is wrong, else the reason it exists — one slot, error first.
                    supportingText = supportingTextOf(
                        error = moneyErrorText(form.price),
                        hint = if (form.price.text.isBlank()) {
                            stringResource(R.string.field_required)
                        } else {
                            stringResource(R.string.group_price_hint)
                        },
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // What the rate used to be, where the rate is typed — the argument the student's reprice
            // dialog makes in its own KDoc. It was an expander on the group's *card*, which is the one
            // screen that cannot change the number it was annotating.
            if (!viewModel.isNew) {
                item(key = "rate-history") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        Text(
                            stringResource(R.string.rate_history_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RateHistoryList(state.rateHistory)
                    }
                }
            }

            item(key = "mode") {
                ModeField(
                    label = stringResource(R.string.student_mode_label),
                    selected = form.mode,
                    onSelect = { m -> viewModel.update { it.copy(mode = m) } },
                )
            }

            item(key = "roster-header") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.tight))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.group_members_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // Counts what would actually be saved, not what is ticked: switching subject
                        // can strand a tick on someone the new subject excludes.
                        if (state.selectedMemberIds.isNotEmpty()) {
                            Text(
                                stringResource(R.string.group_members_selected, state.selectedMemberIds.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    when {
                        // "Nobody is priced for this subject" is a different problem from "you have no
                        // students at all", and only the first is fixed by changing the subject.
                        !state.hasStudents -> Text(
                            stringResource(
                                if (form.subjectId == null) R.string.group_no_students
                                else R.string.group_no_students_for_subject
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        state.selectedMemberIds.isEmpty() -> Text(
                            stringResource(R.string.group_members_required),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            items(state.candidates, key = { it.id }) { student ->
                MemberPickRow(
                    student = student,
                    selected = student.id in form.memberIds,
                    onToggle = { on -> viewModel.toggleMember(student.id, on) },
                )
            }

            // No "danger zone" any more: archiving and deleting the group are lifecycle actions and
            // they live on the group's **card**, behind the overflow, where a student's already did.
            // See [GroupDetailViewModel].
        }
    }
}

// The member picker's row is `MemberPickRow` in ui/common/Fields.kt now — the booking form picks
// who was at a lesson with the same control, which is the same act and used to look nothing like it.
