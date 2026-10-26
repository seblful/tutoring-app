package com.seblful.klas.ui.students

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.labelRes
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.LoadingFormScreen
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.Reveal
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.formBar
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.FormBar
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.template.FormScreen
import com.seblful.klas.ui.subjects.SubjectNameDialog
import com.seblful.klas.ui.theme.KlasMotion
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * A student, new or — given [studentId] — being corrected: one form (D5), with their subjects and rates, saved
 * from the bar, which names what it will do («Add student») and, once nothing is missing, sums up whom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    onBack: () -> Unit,
    viewModel: StudentFormViewModel = assistedMetroViewModel(),
    initialSubjectId: Long? = null,
    onCreated: ((Long) -> Unit)? = null,
    studentId: Long? = null,
) {
    LaunchedEffect(initialSubjectId) { initialSubjectId?.let(viewModel::suggestSubject) }
    LaunchedEffect(studentId) { studentId?.let(viewModel::edit) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.opened == StudentFormViewModel.Opened.Reading) {
        LoadingFormScreen(title = stringResource(R.string.student_edit_title), onBack = onBack, fields = 5)
        return
    }
    val save = { viewModel.create { id -> if (onCreated != null) onCreated(id) else onBack() } }

    // A save that failed must not look like one that went through: the form stays open and says so.
    val messages = rememberMessages()
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(viewModel.events, messages) { saveFailedMsg }

    // Back — the arrow or the system gesture — asks before throwing a filled-in form away.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = save)

    // `StudentFormContent` keeps an `imePadding()` of its own only because onboarding embeds it — a no-op here,
    // where the template has already taken the keyboard's inset.
    FormScreen(
        title = stringResource(if (state.isNew) R.string.student_add_title else R.string.student_edit_title),
        saveLabel = stringResource(if (state.isNew) R.string.student_form_add else R.string.action_save),
        bar = studentBar(state),
        onSave = save,
        onLeave = requestLeave,
        messages = messages,
    ) { fields -> StudentFormContent(state = state, viewModel = viewModel, modifier = fields) }
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
    val pickContact = rememberContactPicker(viewModel::pickContact)
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
                onPickContact = pickContact,
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
    // made here, it arrives ticked. Without it the schedule's «Добавить ученика» on a fresh install would
    // end in a refusal with nowhere to go.
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
        // The rate on its subject's row (R6) — below it only when a large font would leave the name no room.
        val rateBeside = LocalDensity.current.fontScale <= MaxFontScaleForRateBeside
        subjects.forEach { subject ->
            val selected = subject.id in state.selectedSubjectIds
            val rate: @Composable (Modifier) -> Unit = { fieldModifier ->
                val price = form.subjectPrices[subject.id] ?: MoneyField()
                MoneyTextField(
                    field = price,
                    onValueChange = { viewModel.setPriceText(subject.id, it) },
                    label = stringResource(R.string.price_label),
                    hint = lessonPriceHint(price, state.lessonMinutes),
                    modifier = fieldModifier,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectTick(
                        subject.name,
                        selected,
                        locked = subject.id in state.locked,
                        onToggle = { on -> viewModel.toggleSubject(subject.id, on) },
                        modifier = Modifier.weight(1f),
                    )
                    if (rateBeside) {
                        AnimatedVisibility(
                            selected,
                            enter =
                            expandHorizontally(KlasMotion.defaultSpatial()) + fadeIn(KlasMotion.defaultEffects()),
                            exit =
                            shrinkHorizontally(KlasMotion.defaultSpatial()) + fadeOut(KlasMotion.defaultEffects()),
                        ) { rate(Modifier.width(RateFieldWidth)) }
                    }
                }
                // Indented to the width the checkbox takes, so the field sits under the name it prices.
                if (!rateBeside) Reveal(selected) {
                    rate(Modifier.padding(start = PriceFieldIndent, top = Spacing.tight))
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
 * A subject to tick, the whole row the target. [locked]: the student has lessons in it, so it stays — said
 * under the name, rather than a tick that will not move.
 */
@Composable
private fun SubjectTick(
    name: String,
    selected: Boolean,
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.outsetRowPress { source ->
            Modifier.toggleable(
                value = selected,
                interactionSource = source,
                indication = null,
                enabled = !locked,
                role = Role.Checkbox,
                onValueChange = onToggle,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A picture of the state, a thumb tall: `onCheckedChange = null` drops the checkbox's own 48 dp, which
        // left the row 24 dp tall to tap. Only as wide as the tick, though — the whole row is the target, and
        // centred in 48 dp the tick stood 12 dp in from the edge every field above it starts on.
        Box(Modifier.heightIn(min = MinTouchTarget), contentAlignment = Alignment.Center) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = !locked)
        }
        Column(Modifier.padding(start = Spacing.inline)) {
            Text(name)
            if (locked) {
                Text(
                    stringResource(R.string.student_form_subject_has_lessons),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The student form's bar: what is missing, or whom it will save — shared with setup's student step. */
@Composable
internal fun studentBar(state: StudentFormViewModel.UiState): FormBar =
    formBar(state.missing) { FormBar.Ready(state.form.name.trim(), studentSummary(state)) }

/** «Math 25 $/h · In person»: the ticked subjects at their rates, then the place — what the save bar sums up. */
@Composable
private fun studentSummary(state: StudentFormViewModel.UiState): String {
    val subjects = state.allSubjects.valueOrNull().orEmpty()
    val rates = subjects.mapNotNull { subject ->
        state.form.subjectPrices[subject.id]?.amount?.let {
            "${subject.name} ${stringResource(R.string.price_per_hour, it.text())}"
        }
    }
    return bulletJoin(*rates.toTypedArray(), stringResource(state.form.mode.labelRes()))
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

/** «1 250,50» and its label at the default font, beside the subject's name. */
private val RateFieldWidth = 148.dp

/** Past this, the rate goes under its subject: beside it, the name would wrap a letter or two to a line. */
private const val MaxFontScaleForRateBeside = 1.3f

private val SubjectRowHeight = MinTouchTarget
