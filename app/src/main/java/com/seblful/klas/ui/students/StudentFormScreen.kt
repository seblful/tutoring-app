package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.Reveal
import com.seblful.klas.ui.common.SaveAction
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.subjects.SubjectNameDialog
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Full-screen "add student" with required subjects + prices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    onBack: () -> Unit,
    viewModel: StudentFormViewModel = metroViewModel(),
    initialSubjectId: Long? = null,
    onCreated: ((Long) -> Unit)? = null,
) {
    LaunchedEffect(initialSubjectId) { initialSubjectId?.let(viewModel::suggestSubject) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val save = { viewModel.create { id -> if (onCreated != null) onCreated(id) else onBack() } }

    // A save that failed must not look like one that went through: the form stays open and says so.
    val messages = rememberMessages()
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(viewModel.events, messages) { saveFailedMsg }

    // Back — the arrow or the system gesture — asks before throwing a filled-in form away.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = save)

    KlasScreen(
        title = stringResource(R.string.student_add_title),
        onBack = requestLeave,
        messages = messages,
        // The per-subject price fields sit at the foot of the form. The shell owns the rule, as it does
        // for the group and booking forms; `StudentFormContent` keeps its own `imePadding()` only
        // because onboarding embeds it — and that one is a no-op here, the inset already consumed.
        imeAware = true,
        actions = { SaveAction(enabled = state.canSave, onSave = save) },
    ) { modifier, bottomInset ->
        StudentFormContent(state = state, viewModel = viewModel, modifier = modifier, bottomInset = bottomInset)
    }
}

/**
 * The form body, without a scaffold, so it can also be embedded (e.g. in onboarding).
 *
 * @param contacts whether to ask how to reach the student and where they are taught. Setup asks only for
 *   what a lesson needs — a name and a priced subject — and leaves the rest to the card.
 */
@Composable
fun StudentFormContent(
    state: StudentFormViewModel.UiState,
    viewModel: StudentFormViewModel,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
    contacts: Boolean = true,
) {
    val form = state.form
    // The currency is the screen's to know (see `LocalCurrency`); onboarding may change it in the same flow.
    val diallingCode = Phone.prefill(LocalCurrency.current)
    LaunchedEffect(diallingCode) { viewModel.offerDiallingCode(diallingCode) }
    Column(
        modifier
            // Before the scroll, so the keyboard shrinks the viewport instead of covering the
            // per-subject price fields at the foot of the form. Both hosts (`KlasScreen(imeAware = true)`
            // and onboarding) have already applied this, which consumes the inset — so this is the
            // belt-and-braces copy for an embedding that has not, and a no-op for the two that have.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottomInset)
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        if (contacts) {
            StudentContactFields(
                name = form.name,
                onName = { v -> viewModel.update { it.copy(name = v) } },
                phone = form.phone,
                onPhone = { v -> viewModel.update { it.copy(phone = v) } },
                email = form.email,
                onEmail = { v -> viewModel.update { it.copy(email = v) } },
                note = form.note,
                onNote = { v -> viewModel.update { it.copy(note = v) } },
                mode = form.mode,
                onMode = { m -> viewModel.update { it.copy(mode = m) } },
            )
        } else {
            StudentNameField(form.name) { v -> viewModel.update { it.copy(name = v) } }
        }

        KlasDivider(Modifier.padding(vertical = Spacing.tight))
        SectionTitle(stringResource(R.string.student_form_subjects_title))
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

@Composable
private fun SubjectPrices(
    subjects: List<Subject>,
    state: StudentFormViewModel.UiState,
    viewModel: StudentFormViewModel,
) {
    val form = state.form
    // The way out of «Сначала добавьте предметы», and of a new student taking a subject not taught before:
    // made here, it arrives ticked. It used to be a refusal with nowhere to go — the schedule's «Добавить
    // ученика» led to it on a fresh install, and the only road to a subject was back out of the form.
    var addingSubject by rememberSaveable { mutableStateOf(false) }
    if (subjects.isEmpty()) {
        Text(
            stringResource(R.string.student_form_no_subjects),
            color = klasSemantics.validation,
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
            // Keep the price field below the subject row so names remain readable at larger font scales.
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .outsetRowPress { source ->
                            Modifier.toggleable(
                                value = selected,
                                interactionSource = source,
                                indication = null,
                                role = Role.Checkbox,
                                onValueChange = { on -> viewModel.toggleSubject(subject.id, on) },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A picture of the state, a thumb tall: `onCheckedChange = null` drops the checkbox's own
                    // 48 dp, which left the row 24 dp tall to tap. Only as wide as the tick, though — the
                    // whole row is the target, and centred in 48 dp the tick stood 12 dp in from the edge
                    // every field above it starts on.
                    Box(Modifier.heightIn(min = MinTouchTarget), contentAlignment = Alignment.Center) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    }
                    Text(subject.name, Modifier.padding(start = Spacing.inline))
                }
                Reveal(selected) {
                    MoneyTextField(
                        field = price,
                        onValueChange = { viewModel.setPriceText(subject.id, it) },
                        label = stringResource(R.string.price_label),
                        hint = lessonPriceHint(price, state.lessonMinutes),
                        // Indented to the width the checkbox takes, so the field sits under the
                        // name it prices rather than under the tick.
                        modifier = Modifier.padding(start = PriceFieldIndent, top = Spacing.tight),
                    )
                }
            }
        }
    }
    AddRow(stringResource(R.string.subjects_add), onClick = { addingSubject = true })
    if (addingSubject) {
        SubjectNameDialog(onConfirm = viewModel::addSubject, onDismiss = { addingSubject = false })
    }
}

/**
 * What the typed hourly price comes to for a lesson of the usual length — said only when that length is not
 * an hour, which is when "per hour" and "per lesson" stop being the same number. A tutor who charges per
 * lesson thinks in the second, and typed it as the first.
 */
@Composable
private fun lessonPriceHint(price: MoneyField, lessonMinutes: Int): String? {
    val hourly = price.amount?.takeIf { lessonMinutes != 60 } ?: return null
    return stringResource(
        R.string.price_per_lesson_hint,
        ClassRules.chargeFor(hourly, lessonMinutes).text(),
        durationLabel(lessonMinutes),
    )
}

/** Material's checkbox — a 20 dp box in 2 dp of padding — and the gap after it: where the subject's name starts. */
private val PriceFieldIndent = 24.dp + Spacing.inline

private val SubjectRowHeight = MinTouchTarget
