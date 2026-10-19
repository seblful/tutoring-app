package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.formatDate
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.CountPickerModal
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingFormScreen
import com.seblful.klas.ui.common.MemberPickRow
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.PresetChips
import com.seblful.klas.ui.common.SaveAction
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.schedule.timeConflictText
import java.time.LocalDate

@Composable
fun ClassEditScreen(
    onBack: () -> Unit,
    viewModel: ClassEditViewModel = hiltViewModel(),
) {
    val screenState by viewModel.state.collectAsState()
    val gone by viewModel.gone.collectAsState()
    val title = stringResource(if (viewModel.isNew) R.string.class_add_title else R.string.class_edit_title)

    // Said, with the way back, rather than drawn as an empty form over a lesson that no longer exists.
    if (gone) {
        KlasScreen(title = title, onBack = onBack) { modifier ->
            EmptyState(
                title = stringResource(R.string.class_gone),
                detail = stringResource(R.string.class_gone_detail),
                icon = Icons.Outlined.EventBusy,
                modifier = modifier,
            )
        }
        return
    }

    // A lesson being edited waits for itself. Drawn from a blank `ClassForm` this screen states that the
    // lesson has no student, no subject and — for a group booking — that there are no groups at all, in
    // the error red, over a lesson that has all three. See [Loadable]; a new booking is [Loadable.Ready]
    // from the start, because a blank form is what a new booking *is*.
    when (val loaded = screenState) {
        Loadable.Loading -> LoadingFormScreen(title = title, onBack = onBack, fields = 5)
        is Loadable.Ready -> ClassEditForm(loaded.value, title, viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassEditForm(
    state: ClassFormState,
    title: String,
    viewModel: ClassEditViewModel,
    onBack: () -> Unit,
) {
    val form = state.form
    val members = state.members
    val durations by viewModel.durationOptions.collectAsState()

    // A save can still be refused after the button was enabled — say so rather than doing nothing.
    val messages = rememberMessages()
    val timeTakenMsg = stringResource(R.string.class_save_refused_time_taken)
    val noMembersMsg = stringResource(R.string.class_group_no_members_left)
    val allTakenMsg = stringResource(R.string.class_series_refused_all_taken)
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(
        viewModel.events,
        messages,
        // The hour being taken is the app's one hard "no" — felt here as it is on the schedule. A save that
        // merely failed is not a refusal, and a lesson left with nobody in it is a form not finished yet.
        refused = { event ->
            event == ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN ||
                event == ClassEditViewModel.Event.SERIES_REFUSED_ALL_TAKEN
        },
    ) { event ->
        when (event) {
            ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN -> timeTakenMsg
            ClassEditViewModel.Event.SAVE_REFUSED_NO_MEMBERS -> noMembersMsg
            ClassEditViewModel.Event.SERIES_REFUSED_ALL_TAKEN -> allTakenMsg
            ClassEditViewModel.Event.SAVE_FAILED -> saveFailedMsg
        }
    }

    // **Where the app asks to be allowed to remind at all**, once, after the tutor's first lesson is in
    // the database — see [ClassEditViewModel.shouldAskForNotifications] for why here and why only then.
    //
    // The screen shows the prompt and nothing else. The answer is deliberately unhandled — a denial is not
    // refused, corrected or explained here, because the tutor has just booked a lesson and is on their way
    // out of this screen, and the place that reports reminders not working is the one row in Settings that
    // can also do something about it.
    val requestNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) {
        viewModel.askForNotifications.stream.collect { requestNotifications() }
    }

    // A repeating booking that got some of its dates and not others — see [SeriesOutcome]. State rather
    // than a message, because a successful save navigates away and a snackbar on a screen being popped is
    // shown to nobody; the screen leaves when the dialog is dismissed.
    val seriesOutcome by viewModel.seriesOutcome.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showRepeatCountPicker by remember { mutableStateOf(false) }

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

    KlasScreen(
        title = title,
        onBack = requestLeave,
        messages = messages,
        // The keyboard shrinks the viewport rather than covering it; the shell composes that in ahead
        // of the scroll below. A taken time is as disqualifying as a missing field, and `canSave`
        // already says so — the lesson could not happen.
        imeAware = true,
        actions = { SaveAction(enabled = state.canSave, onSave = ::requestSave) },
    ) { modifier ->
        Column(
            modifier
                // The gutter inside the scroll, as the student form has it: padded outside, the fields
                // scrolled within a box 16 dp short of the bar instead of passing under it.
                .verticalScroll(rememberScrollState())
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
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
                    selectedText = state.studentName,
                    options = state.students.map { it.id to it.name },
                    onSelect = { id -> viewModel.change(BookingIntent.ChooseStudent(id)) },
                )

                // Anything that qualifies a field goes in that field's own `supportingText` — this
                // notice was a standalone `Text` under it, which put two hint mechanisms on one form.
                // `isError` because it is a refusal, not a note: nothing can be booked until there is
                // a group, and a grey line saying so reads as advice.
                ClassTarget.GROUP -> DropdownField(
                    label = stringResource(R.string.class_group_label),
                    selectedText = state.groupName,
                    options = state.groups.map { it.id to it.name },
                    onSelect = { id -> viewModel.change(BookingIntent.ChooseGroup(id)) },
                    // A booked lesson can't be handed to another group: its members' classes are
                    // billed history, and the other group is a different set of people.
                    enabled = viewModel.isNew,
                    supportingText = stringResource(R.string.class_no_groups)
                        .takeIf { state.hasNoGroups },
                    isError = state.hasNoGroups,
                )
            }

            DropdownField(
                label = stringResource(R.string.class_subject_label),
                selectedText = state.subjectName,
                options = state.subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.change(BookingIntent.ChooseSubject(id)) },
                enabled = state.subjectPickerEnabled,
                supportingText = state.subjectNotice?.let { notice ->
                    stringResource(
                        when (notice) {
                            SubjectNotice.FIXED_BY_GROUP -> R.string.class_subject_from_group
                            SubjectNotice.NONE_FOR_GROUP -> R.string.class_no_subjects_for_group
                            SubjectNotice.NONE_FOR_STUDENT -> R.string.class_no_subjects_for_student
                        },
                    )
                },
                // «Предмет задан группой» is a fact about how this form works; the other two are the
                // reason nothing can be booked. Only the second kind is red — one grey line for both is
                // how the blocking one stopped being visible.
                isError = state.subjectNotice != null &&
                    state.subjectNotice != SubjectNotice.FIXED_BY_GROUP,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.gutter)) {
                PickerField(
                    value = form.date?.let {
                        it.formatDate()
                    } ?: "",
                    label = stringResource(R.string.class_date_label),
                    placeholder = stringResource(R.string.class_pick_date),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f),
                )
                // Marked as the field that is wrong — it is the only thing that *can* be wrong about a
                // time now, since a start and a length cannot be impossible. The sentence itself is
                // **not** in this field's supporting slot: the field is `weight(1f)` of a 1.4 : 1 row,
                // which is about 98dp of text width on a 360dp phone, and «Занято: Мария Петрова
                // 14:00 – 15:30» wraps to five lines in that. It goes full-width under the row instead.
                PickerField(
                    value = form.timeFrom?.timeText() ?: "",
                    label = stringResource(R.string.class_time_from_label),
                    placeholder = stringResource(R.string.class_pick_time),
                    onClick = { showFromPicker = true },
                    modifier = Modifier.weight(1f),
                    isError = state.conflict != null,
                )
            }
            state.conflict?.let { conflict ->
                Text(
                    timeConflictText(conflict),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // How long, not when it ends — a length is what the lesson is stored as, and the question
            // the tutor already has an answer to. The end is shown beside the label as a consequence.
            DurationPicker(
                options = durations,
                selected = form.durationMinutes,
                ends = state.ending,
                onSelect = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)) },
                onCustom = { showDurationPicker = true },
            )

            // Whether this books one lesson or a term of them. **Only while booking**: repeating is a
            // property of the act, not of a lesson, so there is nothing for it to say about a row already
            // in the database — and offering it there would read as an invitation to turn one lesson into
            // twelve by editing it.
            if (viewModel.isNew) {
                RepeatPicker(
                    repeat = form.repeat,
                    count = form.repeatCount,
                    until = form.repeatsUntil,
                    onRepeat = { repeat -> viewModel.change(BookingIntent.Edit { it.copy(repeat = repeat) }) },
                    onCount = { count -> viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) }) },
                    onCustomCount = { showRepeatCountPicker = true },
                )
            }

            if (form.target == ClassTarget.GROUP) {
                // Who the lesson covers — ticked one by one, and the same list whether it is being
                // booked or corrected afterwards. Someone away this week is not off the roster, so
                // the tick lives on the lesson and the group is untouched. This is the one place a
                // lesson's attendance is set: the schedule's action menu changes the whole lesson.
                if (state.groupHasNoMembers) {
                    Text(
                        stringResource(R.string.class_group_no_active_members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (members.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.class_group_members_title))
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
            }

            // **The rate, never folded.** For a group it is *n* students' money; for one student it is
            // the number that will be billed. It used to be a [PickerField] that a tap replaced with a
            // text field for a student and a plain text field for a group — the same input, two
            // appearances, on the same form, decided by who the lesson is for. The fold saved one line
            // and cost the tutor the one fact on the form that is about money: whether the pre-filled
            // rate is the one they meant was readable only after a tap.
            //
            // The label is the only thing that still differs, because the question does: a group's rate
            // is the group's, a student's is theirs for this subject.
            MoneyTextField(
                field = form.price,
                onValueChange = { text ->
                    viewModel.change(BookingIntent.Edit { it.copy(price = it.price.typed(text)) })
                },
                label = stringResource(
                    if (form.target == ClassTarget.GROUP) R.string.class_group_price_label
                    else R.string.price_label
                ),
            )

            // Never folded either, and for its own reason: a mode is not a rate the tutor already
            // agreed, it is a guess from the student's usual place that this lesson may contradict — so
            // it has to be readable, and changeable, without a tap to reveal it first.
            ModeField(
                label = stringResource(R.string.class_location_label),
                selected = form.mode,
                onSelect = { selected -> viewModel.change(BookingIntent.Edit { it.copy(mode = selected) }) },
            )
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
    if (showDurationPicker) {
        DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { minutes ->
                viewModel.change(BookingIntent.LastsFor(minutes))
                showDurationPicker = false
            },
            onDismiss = { showDurationPicker = false },
        )
    }
    if (showRepeatCountPicker) {
        CountPickerModal(
            title = stringResource(R.string.class_repeat_count_title),
            label = stringResource(R.string.class_repeat_count_field, Recurrence.MAX_COUNT),
            initial = viewModel.repeatCountPickerDefault(),
            max = Recurrence.MAX_COUNT,
            onConfirm = { count ->
                viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) })
                showRepeatCountPicker = false
            },
            onDismiss = { showRepeatCountPicker = false },
        )
    }

    // Dismissing **is** leaving: the lessons are already written, so there is nothing to decide here and
    // only one button. What it must not do is let the form close on its own before the tutor has read
    // which dates were skipped — that is the whole reason the save held the navigation.
    seriesOutcome?.let { outcome ->
        val leave = { viewModel.acknowledgeSeries(); onBack() }
        NoticeDialog(
            text = seriesOutcomeText(outcome),
            onDismiss = leave,
            title = stringResource(R.string.class_series_title),
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
    // Segments, not chips. Every other mutually-exclusive choice in the app is a segmented row — the
    // schedule's view mode, the lesson status, People's two sides, four settings rows — and this one sat
    // at the top of the most-used form looking like the duration chips below it, which are chips for a
    // reason (a shortlist with an escape hatch). See [SegmentedChoice].
    SegmentedChoice(
        options = ClassTarget.entries,
        selected = selected,
        onSelect = onSelect,
        label = { target ->
            stringResource(
                when (target) {
                    ClassTarget.STUDENT -> R.string.class_target_student
                    ClassTarget.GROUP -> R.string.class_target_group
                }
            )
        },
    )
}

/**
 * How long the lesson runs, as chips.
 *
 * The chips are the primary answer and «Другая» asks for the rest in hours and minutes — a 50-minute
 * lesson is unusual, not impossible. The end is shown beside the label rather than as a field of its
 * own: it is a *consequence* of the choice, worth confirming and not worth a picker.
 *
 * Always live. The chips used to be disabled until a start was chosen, because the end they set was
 * `start + minutes` and there was nothing to add to; a length stands on its own, so «1,5 ч» can be
 * answered before «Начало» like every other field on this form. [ends] is what waits for a start.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPicker(
    options: List<Int>,
    selected: Int?,
    ends: ClassForm.Ending?,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        // A flow, so the consequence moves to a line of its own whole rather than being squeezed into a
        // column beside the label — «Длительность · до 00:00 (следующего дня)» at a large font scale.
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.class_duration_label))
            if (ends != null) {
                Text(
                    " · " + endsAtText(ends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PresetChips(
            presets = options,
            selected = selected,
            label = { minutes -> durationLabel(minutes) },
            customLabel = stringResource(R.string.class_duration_custom),
            onSelect = onSelect,
            onCustom = onCustom,
        )
    }
}

/**
 * Whether this booking writes one lesson or a run of them.
 *
 * **Deliberately the same shape as [DurationPicker] one field above**: a label carrying the consequence
 * of the choice beside it, then the control. The two questions are neighbours in the tutor's head — how
 * long, and how often — and answering them in two different-looking ways on one form is the kind of drift
 * `ui/common` exists to stop.
 *
 * Segments for the frequency, chips for the count, because that is what each *is*: «Повторять» is a
 * choice between a fixed, mutually exclusive few, and the count is a shortlist with an escape hatch. The
 * count only appears once something is being repeated — asking "how many lessons?" of a booking that is
 * one lesson is a field with a single legal answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(
    repeat: Repeat,
    count: Int,
    until: LocalDate?,
    onRepeat: (Repeat) -> Unit,
    onCount: (Int) -> Unit,
    onCustomCount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        // A flow, so the consequence moves to a line of its own whole rather than being squeezed into a
        // column beside the label — «Длительность · до 00:00 (следующего дня)» at a large font scale.
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.class_repeat_label))
            // «по 24 сентября» — the last day the run reaches. Shown only once there is a date to count
            // from, exactly as the lesson's end waits for a start.
            if (until != null) {
                Text(
                    " · " + stringResource(R.string.class_repeat_until, until.formatDate()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SegmentedChoice(
            options = Repeat.entries,
            selected = repeat,
            onSelect = onRepeat,
            label = { option ->
                stringResource(
                    when (option) {
                        Repeat.NONE -> R.string.class_repeat_none
                        Repeat.WEEKLY -> R.string.class_repeat_weekly
                        Repeat.FORTNIGHTLY -> R.string.class_repeat_fortnightly
                    }
                )
            },
        )
        if (repeat.repeats) {
            Text(
                stringResource(R.string.class_repeat_count_label),
                style = MaterialTheme.typography.labelLarge,
            )
            PresetChips(
                presets = Recurrence.COMMON_COUNTS,
                selected = count,
                label = { option -> option.toString() },
                customLabel = stringResource(R.string.class_repeat_count_custom),
                onSelect = onCount,
                onCustom = onCustomCount,
            )
        }
    }
}

/**
 * What a partly-booked run says: how many lessons went in, then which dates did not and why.
 *
 * The counts are `SeriesOutcome`'s and the dates are its own; all this owns is the wording — including
 * that the skipped dates are **listed** rather than counted, since finding those Mondays is the thing the
 * tutor now has to do.
 */
@Composable
private fun seriesOutcomeText(outcome: SeriesOutcome): String {
    val booked = pluralStringResource(R.plurals.class_series_booked, outcome.booked, outcome.booked)
    val skipped = pluralStringResource(
        R.plurals.class_series_skipped,
        outcome.skipped.size,
        outcome.skipped.size,
    )
    return "$booked\n\n$skipped ${outcome.skipped.joinToString { it.formatDate() }}"
}

/**
 * «до 20:00», and «до 00:00 (след. день)» for a lesson that runs past midnight.
 *
 * The marker is the whole reason [ClassForm.Ending] carries a flag: a bare «до 00:00» under a date of
 * «29 июль» is the one end that could be read as the start of the day being booked rather than its end.
 */
@Composable
private fun endsAtText(ends: ClassForm.Ending): String {
    val time = ends.time.timeText()
    return if (ends.nextDay) {
        stringResource(R.string.class_duration_ends_next_day, time)
    } else {
        stringResource(R.string.class_duration_ends_at, time)
    }
}

// PickerField and DropdownField live in ui/common/Fields.kt — every screen that hides a value
// behind a tap has to draw it the same way, which is exactly what this form was not doing.
