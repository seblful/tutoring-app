package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingFormScreen
import com.seblful.klas.ui.common.MemberPickRow
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.RateHistory
import com.seblful.klas.ui.common.SaveAction
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.supportingTextOf
import com.seblful.klas.ui.students.AddSubjectPriceDialog
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** reate or edit one group: **subject**, name, note, hourly rate, default mode and roster. */
@Composable
fun GroupFormScreen(
    onBack: () -> Unit,
    viewModel: GroupFormViewModel = assistedMetroViewModel(),
    onAddStudent: (Long?) -> Unit = {},
    onSaved: ((Long) -> Unit)? = null,
) {
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val title = stringResource(
        if (viewModel.isNew) R.string.group_add_title else R.string.group_edit_title,
    )

    // An existing group's form waits rather than drawing itself from a blank [GroupFormViewModel.Form]:
    // every field here is a stored value, and the blank version of this screen states that the group has
    // no name, no rate and — in the error red — that the tutor has no students. See [Loadable].
    when (val loaded = screenState) {
        Loadable.Loading -> LoadingFormScreen(title = title, onBack = onBack, fields = 5)
        is Loadable.Ready -> GroupForm(loaded.value, title, viewModel, onBack, onAddStudent, onSaved)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupForm(
    state: GroupFormViewModel.UiState,
    title: String,
    viewModel: GroupFormViewModel,
    onBack: () -> Unit,
    onAddStudent: (Long?) -> Unit,
    onSaved: ((Long) -> Unit)?,
) {
    val form = state.form
    val saved: (Long) -> Unit = { id -> if (onSaved != null) onSaved(id) else onBack() }
    var pricingMemberId by rememberSaveable { mutableStateOf<Long?>(null) }
    GroupMemberPrice(state, pricingMemberId, viewModel::enrollMember) { pricingMemberId = null }

    // A save that would take somebody off the roster — no price for the group's subject — asks first.
    var confirmingDrops by rememberSaveable { mutableStateOf(false) }
    fun requestSave() {
        if (state.leavingOnSave > 0) confirmingDrops = true else viewModel.save(saved)
    }

    // Back — the arrow or the system gesture — asks before throwing away edits to a group, and saving on
    // the way out owes the same question as the save button.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = ::requestSave)

    val messages = rememberMessages()
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(viewModel.events, messages) { saveFailedMsg }

    if (confirmingDrops) {
        ConfirmDialog(
            text = pluralStringResource(
                R.plurals.group_members_leaving_confirm,
                state.leavingOnSave,
                state.leavingOnSave,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { viewModel.save(saved) },
            onDismiss = { confirmingDrops = false },
        )
    }

    KlasScreen(
        title = title,
        onBack = requestLeave,
        messages = messages,
        // The rate field and the roster are at the bottom of a long form, and under edge-to-edge the
        // IME is an inset rather than a resize — the shell composes it in ahead of the scroll.
        imeAware = true,
        actions = { SaveAction(enabled = state.canSave, onSave = ::requestSave) },
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
                    // Capitalised like a subject's name, which is what a group's is closest to — not word by
                    // word like a person's.
                    keyboardOptions = TitleKeyboard,
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
                        else R.string.group_subject_hint,
                    ),
                )
            }

            item(key = "note") {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                    label = { Text(stringResource(R.string.student_note_label)) },
                    // The student form's note keyboard: the same field, so the same first capital.
                    keyboardOptions = TitleKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "price") {
                // The group's own rate: what each member of its lessons is billed, whatever the
                // subject. Required — a group prices its own lessons, so it has to say what they cost —
                // and shown plainly rather than behind a tap, because it is the substance of the group,
                // not a detail.
                MoneyTextField(
                    field = form.price,
                    onValueChange = { v -> viewModel.update { it.copy(price = it.price.typed(v)) } },
                    label = stringResource(R.string.group_price_label),
                    // The reason it exists, unless it is wrong — and blank is required-and-unfilled, which
                    // the hint says without shouting.
                    hint = if (form.price.text.isBlank()) {
                        stringResource(R.string.field_required)
                    } else {
                        stringResource(R.string.group_price_hint)
                    },
                )
            }

            // What the rate used to be, where the rate is typed — the argument the student's reprice
            // dialog makes in its own KDoc. It was an expander on the group's *card*, which is the one
            // screen that cannot change the number it was annotating.
            if (!viewModel.isNew) {
                item(key = "rate-history") { RateHistory(state.rateHistory) }
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
                        SectionTitle(
                            stringResource(R.string.group_members_title),
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
                                else R.string.group_no_students_for_subject,
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

            if (state.unpriced.isNotEmpty()) {
                item(key = "unpriced-help") {
                    Text(
                        stringResource(R.string.group_price_setup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.itemGap),
                    )
                }
                // Laid out as the member rows above, so the roster reads as one list: the same avatar and
                // name, with the step that makes them eligible where the checkbox would be.
                items(state.unpriced, key = { "unpriced:${it.id}" }) { student ->
                    UnpricedMemberRow(student, onSetPrice = { pricingMemberId = student.id })
                }
            }
            item(key = "add-student") {
                AddRow(stringResource(R.string.schedule_add_student), onClick = { onAddStudent(form.subjectId) })
            }

            // No "danger zone" any more: archiving and deleting the group are lifecycle actions and
            // they live on the group's **card**, behind the overflow, where a student's already did.
            // See [GroupDetailViewModel].
        }
    }
}

@Composable
private fun UnpricedMemberRow(student: Student, onSetPrice: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = Spacing.inline, vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(student.name, student.id)
        Text(
            student.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.leading),
        )
        TextButton(onClick = onSetPrice) { Text(stringResource(R.string.group_member_set_price)) }
    }
}

@Composable
private fun GroupMemberPrice(
    state: GroupFormViewModel.UiState,
    memberId: Long?,
    onEnroll: (Long, Long, Money) -> Unit,
    onDismiss: () -> Unit,
) {
    val member = state.unpriced.firstOrNull { it.id == memberId } ?: return
    val subject = state.subjects.firstOrNull { it.id == state.form.subjectId } ?: return
    AddSubjectPriceDialog(
        addableSubjects = listOf(subject),
        noSubjectsYet = false,
        initialSubjectId = subject.id,
        title = member.name,
        onConfirm = { subjectId, amount -> onEnroll(member.id, subjectId, amount) },
        onDismiss = onDismiss,
    )
}

// The group and booking forms share MemberPickRow for roster membership.

private val TitleKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
