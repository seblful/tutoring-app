package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.KlasScreen
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
        if (state.allSubjects.isEmpty()) {
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
            state.allSubjects.forEach { subject ->
                val selected = subject.id in state.selectedSubjectIds
                val price = form.subjectPrices[subject.id] ?: MoneyField()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
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
                        Spacer(Modifier.width(Spacing.inline))
                        OutlinedTextField(
                            value = price.text,
                            onValueChange = { viewModel.setPriceText(subject.id, it) },
                            label = { Text(stringResource(R.string.price_label)) },
                            isError = price.isError,
                            // Short enough to fit this box — see the note at `money_error_unreadable`.
                            supportingText = supportingTextOf(moneyErrorText(price)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(PriceFieldWidth),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wide enough for «1 250,50 BYN» and narrow enough to leave a subject name its room, in a row that
 * holds a checkbox, a name and this. A measurement, not a gap — see `Spacing`.
 */
private val PriceFieldWidth = 140.dp
