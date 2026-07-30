package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.SaveAction
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.moneyErrorText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.supportingTextOf

/** Full-screen "add student" with required subjects + prices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    onBack: () -> Unit,
    viewModel: StudentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // A save that failed must not look like one that went through: the form stays open and says so.
    val messages = rememberMessages()
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(viewModel.events, messages) { saveFailedMsg }

    // Back — the arrow or the system gesture — asks before throwing a filled-in form away.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = { viewModel.save(onBack) })

    KlasScreen(
        title = stringResource(R.string.student_add_title),
        onBack = requestLeave,
        messages = messages,
        // The per-subject price fields sit at the foot of the form. The shell owns the rule, as it does
        // for the group and booking forms; `StudentFormContent` keeps its own `imePadding()` only
        // because onboarding embeds it — and that one is a no-op here, the inset already consumed.
        imeAware = true,
        actions = { SaveAction(enabled = state.canSave, onSave = { viewModel.save(onBack) }) },
    ) { modifier ->
        StudentFormContent(state = state, viewModel = viewModel, modifier = modifier)
    }
}

/** The form body, without a scaffold, so it can also be embedded (e.g. in onboarding). */
@Composable
fun StudentFormContent(
    state: StudentFormViewModel.UiState,
    viewModel: StudentFormViewModel,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier
            // Before the scroll, so the keyboard shrinks the viewport instead of covering the
            // per-subject price fields at the foot of the form. Both hosts (`KlasScreen(imeAware = true)`
            // and onboarding) have already applied this, which consumes the inset — so this is the
            // belt-and-braces copy for an embedding that has not, and a no-op for the two that have.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text(stringResource(R.string.student_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = { v -> viewModel.update { it.copy(phone = v) } },
            label = { Text(stringResource(R.string.student_phone_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.email,
            onValueChange = { v -> viewModel.update { it.copy(email = v) } },
            label = { Text(stringResource(R.string.student_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { v -> viewModel.update { it.copy(note = v) } },
            label = { Text(stringResource(R.string.student_note_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ModeField(
            label = stringResource(R.string.student_mode_label),
            selected = form.mode,
            onSelect = { m -> viewModel.update { it.copy(mode = m) } },
        )

        HorizontalDivider(Modifier.padding(vertical = Spacing.tight))
        Text(
            stringResource(R.string.student_form_subjects_title),
            style = MaterialTheme.typography.titleMedium,
        )
        // «Сначала добавьте предметы» is a refusal, and saying it about subjects that have merely not
        // been read yet is the [Loadable] defect — here in the error red, on the screen a tutor reaches
        // by tapping «Добавить». Three rows of skeleton stand in until the list has actually arrived.
        when (val loaded = state.allSubjects) {
            Loadable.Loading ->
                ListSkeleton(rows = 3, rowHeight = SubjectRowHeight, contentPadding = PaddingValues(0.dp))

            is Loadable.Ready -> SubjectPrices(loaded.value, state, viewModel)
        }
    }
}

/**
 * The subjects to tick, each with the price this student is charged for it — and the refusal when there
 * are none to tick.
 *
 * Its own composable so the branch above it can be a `when` over three cases rather than an early return
 * out of the form's `Column`. Emits its rows as siblings into that `Column`, so they keep its spacing.
 */
@Composable
private fun SubjectPrices(
    subjects: List<Subject>,
    state: StudentFormViewModel.UiState,
    viewModel: StudentFormViewModel,
) {
    val form = state.form
    if (subjects.isEmpty()) {
        Text(
            stringResource(R.string.student_form_no_subjects),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(
            stringResource(R.string.student_form_subjects_required),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        subjects.forEach { subject ->
            val selected = subject.id in state.selectedSubjectIds
            val price = form.subjectPrices[subject.id] ?: MoneyField()
            // **The tick and the rate stack; they no longer share a row.** The price field was a
            // fixed 140 dp beside a checkbox and a subject name, which is a third of a 360 dp phone
            // spent on a box before the name gets any — and at a large font scale the name was
            // squeezed to an ellipsis while «Цена» kept every dp of its own. Stacked, the name has
            // the whole width and the rate has the whole width, at every scale, and the field's
            // indent is what says the rate belongs to the subject above it.
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selected,
                            onValueChange = { on -> viewModel.toggleSubject(subject.id, on) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Text(subject.name, Modifier.padding(start = Spacing.inline))
                }
                if (selected) {
                    OutlinedTextField(
                        value = price.text,
                        onValueChange = { viewModel.setPriceText(subject.id, it) },
                        label = { Text(stringResource(R.string.price_label)) },
                        isError = price.isError,
                        supportingText = supportingTextOf(moneyErrorText(price)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        // Indented to the width the checkbox takes, so the field sits under the
                        // name it prices rather than under the tick.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = PriceFieldIndent, top = Spacing.tight),
                    )
                }
            }
        }
    }
}

/**
 * How far the rate field is indented under the subject it prices, so its left edge lands under the
 * subject's **name** rather than under its tick: the `Checkbox`'s own 48 dp touch target, plus the same
 * [Spacing.inline] the name is set in from it. A measurement of two other controls, not a gap — see
 * `Spacing`.
 */
private val PriceFieldIndent = MinTouchTarget + Spacing.inline

/** A tick and a subject name — the row the skeleton stands in for while the subjects are being read. */
private val SubjectRowHeight = MinTouchTarget
