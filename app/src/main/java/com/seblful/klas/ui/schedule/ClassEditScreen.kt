package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.MemberPickRow
import com.seblful.klas.ui.common.ModeSelector
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.format.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditScreen(
    onBack: () -> Unit,
    viewModel: ClassEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.form
    val members = state.members
    val durations by viewModel.durationOptions.collectAsState()

    // A save can still be refused after the button was enabled — say so rather than doing nothing.
    val messages = rememberMessages()
    val timeTakenMsg = stringResource(R.string.class_save_refused_time_taken)
    val noMembersMsg = stringResource(R.string.class_group_no_members_left)
    val saveFailedMsg = stringResource(R.string.save_failed)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            val message = when (event) {
                ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN -> timeTakenMsg
                ClassEditViewModel.Event.SAVE_REFUSED_NO_MEMBERS -> noMembersMsg
                ClassEditViewModel.Event.SAVE_FAILED -> saveFailedMsg
            }
            messages.show(message)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var showPriceField by remember { mutableStateOf(false) }

    // Whether saving needs asking about, and how many billed lessons the question counts, are both
    // `state.saveIntent`'s — see [SaveIntent]. What is held here is only whether the dialog is up.
    var confirmingDrops by remember { mutableStateOf<SaveIntent.ConfirmBilledDrops?>(null) }
    fun requestSave() {
        when (val intent = state.saveIntent) {
            is SaveIntent.ConfirmBilledDrops -> confirmingDrops = intent
            SaveIntent.Save -> viewModel.save(onBack)
        }
    }

    // Back — the arrow or the system gesture — asks before throwing away a half-booked lesson.
    // Saving on the way out owes the same billed-drops warning as the save button, so the guard
    // answers «Сохранить» through the same request.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = ::requestSave)

    Scaffold(
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.class_add_title else R.string.class_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // A taken time is as disqualifying as a missing field: the lesson could not happen.
                    TextButton(
                        onClick = ::requestSave,
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .constrainedContent()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Who the lesson is for. Only offered while booking: an existing lesson already is one
            // kind or the other, and turning a student's class into a group lesson (or back) would
            // silently create or destroy other students' billed classes.
            if (viewModel.isNew) {
                TargetSelector(selected = form.target, onSelect = { viewModel.change(BookingIntent.ChooseTarget(it)) })
            }

            when (form.target) {
                ClassTarget.STUDENT -> DropdownField(
                    label = stringResource(R.string.class_student_label),
                    selectedText = state.students.firstOrNull { it.id == form.studentId }?.name ?: "",
                    options = state.students.map { it.id to it.name },
                    onSelect = { id -> viewModel.change(BookingIntent.ChooseStudent(id)) },
                )

                ClassTarget.GROUP -> {
                    DropdownField(
                        label = stringResource(R.string.class_group_label),
                        selectedText = state.groups.firstOrNull { it.id == form.groupId }?.name ?: "",
                        options = state.groups.map { it.id to it.name },
                        onSelect = { id -> viewModel.change(BookingIntent.ChooseGroup(id)) },
                        // A booked lesson can't be handed to another group: its members' classes are
                        // billed history, and the other group is a different set of people.
                        enabled = viewModel.isNew,
                    )
                    if (state.hasNoGroups) {
                        Text(
                            stringResource(R.string.class_no_groups),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            DropdownField(
                label = stringResource(R.string.class_subject_label),
                selectedText = state.subjects.firstOrNull { it.id == form.subjectId }?.name ?: "",
                options = state.subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.change(BookingIntent.ChooseSubject(id)) },
                enabled = state.subjectPickerEnabled,
            )
            state.subjectNotice?.let { notice ->
                Text(
                    stringResource(
                        when (notice) {
                            SubjectNotice.FIXED_BY_GROUP -> R.string.class_subject_from_group
                            SubjectNotice.NONE_FOR_GROUP -> R.string.class_no_subjects_for_group
                            SubjectNotice.NONE_FOR_STUDENT -> R.string.class_no_subjects_for_student
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notice == SubjectNotice.FIXED_BY_GROUP) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PickerField(
                    value = form.date?.let {
                        it.formatDate()
                    } ?: "",
                    label = stringResource(R.string.class_date_label),
                    placeholder = stringResource(R.string.class_pick_date),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f),
                )
                PickerField(
                    value = form.timeFrom?.timeText() ?: "",
                    label = stringResource(R.string.class_time_from_label),
                    placeholder = stringResource(R.string.class_pick_time),
                    onClick = { showFromPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            // How long, not when it ends. The end time is still what gets stored and is still
            // editable through «Другая» — but the question the form asks is the one the tutor
            // already has an answer to.
            DurationPicker(
                options = durations,
                selected = form.durationMinutes,
                endsAt = form.timeTo?.timeText(),
                enabled = form.timeFrom != null,
                onSelect = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)) },
                onCustom = { showToPicker = true },
            )

            // Why the time won't do — said here rather than left to a greyed-out save button.
            state.timeProblem?.let { problem ->
                Text(
                    when (problem) {
                        TimeProblem.RangeInvalid -> stringResource(R.string.class_time_range_invalid)
                        is TimeProblem.Taken -> stringResource(
                            R.string.class_time_conflict,
                            problem.conflict.who,
                            "${problem.conflict.slot.start.timeText()} – " +
                                problem.conflict.slot.end.timeText(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (form.target == ClassTarget.GROUP) {
                // Who the lesson covers — ticked one by one, and the same list whether it is being
                // booked or corrected afterwards. Someone away this week is not off the roster, so
                // the tick lives on the lesson and the group is untouched. This is the one place a
                // lesson's attendance is set: the schedule's action menu changes the whole lesson.
                if (members.isNotEmpty()) {
                    Text(
                        stringResource(R.string.class_group_members_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // On a booked lesson the ticks read as a record rather than a plan, so they say
                    // which question they are answering.
                    if (!viewModel.isNew) {
                        Text(
                            stringResource(R.string.class_group_members_editable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The same row the group form's roster picker uses (see [MemberPickRow]) —
                    // avatar, tinted while selected, one tap target. The two lists used to disagree:
                    // picking who is in a group looked one way, picking who was at a lesson looked
                    // like a bare name and a checkbox, and they are the same act.
                    members.forEach { student ->
                        MemberPickRow(
                            student = student,
                            selected = student.id !in form.excludedMemberIds,
                            onToggle = { on -> viewModel.change(BookingIntent.IncludeMember(student.id, on)) },
                        )
                    }
                    if (state.allMembersExcluded) {
                        Text(
                            stringResource(R.string.class_group_no_members_left),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // The group's rate, pre-filled from the group and editable for this one lesson. Shown
                // plainly rather than behind a tap: for a group lesson it is *n* students' money.
                OutlinedTextField(
                    value = form.price.text,
                    onValueChange = { text ->
                        viewModel.change(BookingIntent.Edit { it.copy(price = it.price.typed(text)) })
                    },
                    label = { Text(stringResource(R.string.class_group_price_label)) },
                    isError = form.price.isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (showPriceField) {
                // Price is pre-filled from the student's rate and usually right, so it stays folded
                // until tapped — but as a *field*, not as a line of text. It used to render as a bare
                // clickable `Text` directly beneath five bordered fields, which is the same input
                // drawn three different ways on one form: an ordinary field, a picker, and a label.
                OutlinedTextField(
                    value = form.price.text,
                    onValueChange = { text ->
                        viewModel.change(BookingIntent.Edit { it.copy(price = it.price.typed(text)) })
                    },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = form.price.isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PickerField(
                    value = form.price.amount?.text() ?: "",
                    label = stringResource(R.string.price_label),
                    placeholder = stringResource(R.string.price_not_set),
                    onClick = { showPriceField = true },
                )
            }

            if (showModeSelector) {
                Text(stringResource(R.string.class_location_label), style = MaterialTheme.typography.labelLarge)
                ModeSelector(
                    selected = form.mode,
                    onSelect = { selected -> viewModel.change(BookingIntent.Edit { it.copy(mode = selected) }) },
                )
            } else {
                // Folded away like the price, and now drawn like it too.
                PickerField(
                    value = classModeLabel(form.mode),
                    label = stringResource(R.string.class_location_label),
                    onClick = { showModeSelector = true },
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = viewModel.datePickerDefault(),
            onConfirm = { date ->
                viewModel.change(BookingIntent.Edit { it.copy(date = date) })
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showFromPicker) {
        TimePickerModal(
            initial = viewModel.startPickerDefault(),
            onConfirm = { time ->
                viewModel.change(BookingIntent.StartAt(time))
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        TimePickerModal(
            initial = viewModel.endPickerDefault(),
            onConfirm = { time ->
                viewModel.change(BookingIntent.EndAt(time))
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }

    confirmingDrops?.let { confirm ->
        ConfirmDialog(
            text = pluralStringResource(
                R.plurals.class_member_drop_billed_confirm,
                confirm.billedLessons,
                confirm.billedLessons,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { viewModel.save(onBack) },
            onDismiss = { confirmingDrops = null },
        )
    }
}


/** Student-or-group chooser for a new booking. */
@Composable
private fun TargetSelector(selected: ClassTarget, onSelect: (ClassTarget) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClassTarget.entries.forEach { target ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = {
                    Text(
                        stringResource(
                            when (target) {
                                ClassTarget.STUDENT -> R.string.class_target_student
                                ClassTarget.GROUP -> R.string.class_target_group
                            }
                        )
                    )
                },
            )
        }
    }
}

/**
 * How long the lesson runs, as chips.
 *
 * The chips are the primary answer and «Другая» keeps the clock picker for the rest — a 50-minute
 * lesson is unusual, not impossible. The end time is shown beside the label rather than as a field of
 * its own: it is now a *consequence* of the choice, worth confirming and not worth a picker.
 *
 * Disabled until a start is chosen, because a duration with nothing to count from means nothing. The
 * label carries the state; the chips simply stop responding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPicker(
    options: List<Int>,
    selected: Int?,
    endsAt: String?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.class_duration_label),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled && endsAt != null) {
                Text(
                    " · " + stringResource(R.string.class_duration_ends_at, endsAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { minutes ->
                FilterChip(
                    selected = enabled && minutes == selected,
                    onClick = { onSelect(minutes) },
                    enabled = enabled,
                    label = { Text(durationLabel(minutes)) },
                )
            }
            // Selected when the chosen length is not one of the offered ones — otherwise editing a
            // lesson booked at 50 minutes would show no chip lit and look like nothing was set.
            FilterChip(
                selected = enabled && selected != null && selected !in options,
                onClick = onCustom,
                enabled = enabled,
                label = { Text(stringResource(R.string.class_duration_custom)) },
            )
        }
    }
}

/** «1 ч», «1,5 ч», «45 мин» — whole and half hours read better than a minute count. */
@Composable
private fun durationLabel(minutes: Int): String = when (minutes) {
    60 -> stringResource(R.string.hours_one)
    90 -> stringResource(R.string.hours_one_and_half)
    120 -> stringResource(R.string.hours_two)
    else -> stringResource(R.string.minutes_short, minutes)
}

// PickerField and DropdownField live in ui/common/Components.kt — every screen that hides a value
// behind a tap has to draw it the same way, which is exactly what this form was not doing.
