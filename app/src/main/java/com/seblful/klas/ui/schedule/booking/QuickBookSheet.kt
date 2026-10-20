package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.formatDate
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.MenuOptions
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SheetHandle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.schedule.SheetGutter
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Booking from the schedule without leaving it: who, then a row per fact, each one already filled in from
 * that person's last lesson (see [BookingForm.filledFrom]) and changed with a tap. The rules, the conflict
 * check and the save are [ClassEditViewModel]'s, the same as the full form's; «All fields» carries the
 * booking there as it stands, for what the sheet leaves out — rosters, counts, setup detours.
 *
 * The full form never folds the rate or the place behind a tap. Here both stay **readable** on their rows,
 * which was that rule's reason; only changing them takes the tap, which is the trade a short form makes.
 *
 * Dismissing discards the booking without asking: it is a few taps, most of them made by the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBookSheet(
    day: LocalDate,
    onDismiss: () -> Unit,
    /** «All fields»: the full form, given this booking as its `draft`. */
    onAllFields: (draft: String) -> Unit,
) {
    val viewModel = rememberSheetViewModel(day)
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slides away first, as a swipe down would, and only then leaves — see the lesson sheet's `closeSheet`.
    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); then() }
    }

    // A snackbar would be drawn under the sheet, so a refused save is said on it instead.
    var refusal by remember { mutableStateOf<ClassEditViewModel.Event?>(null) }
    val reject = rememberRejectFeedback()
    LaunchedEffect(viewModel) {
        viewModel.events.stream.collect { event ->
            refusal = event
            if (event == ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN ||
                event == ClassEditViewModel.Event.SERIES_REFUSED_ALL_TAKEN
            ) {
                reject()
            }
        }
    }
    // As the full form does, after the tutor's first lesson — see [ClassEditViewModel.shouldAskForNotifications].
    val requestNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(viewModel) {
        viewModel.askForNotifications.stream.collect { requestNotifications() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = SheetGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Text(
                stringResource(R.string.class_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = SheetGutter),
            )
            (loaded as? Loadable.Ready)?.value?.let { state ->
                QuickBookBody(
                    state = state,
                    viewModel = viewModel,
                    refusal = refusal,
                    onBook = {
                        refusal = null
                        viewModel.save(onDone = { close() })
                    },
                    onAllFields = {
                        val draft = viewModel.draft()
                        close { onAllFields(draft) }
                    },
                )
            }
        }
    }

    // Dismissing is leaving, as on the full form: the lessons are written, and this says which were not.
    val seriesOutcome by viewModel.seriesOutcome.collectAsStateWithLifecycle()
    seriesOutcome?.let { outcome ->
        NoticeDialog(
            text = seriesOutcomeText(outcome),
            onDismiss = { viewModel.acknowledgeSeries(); close() },
            title = stringResource(R.string.class_series_title),
        )
    }
}

/**
 * A booking form of its own for each time the sheet opens, gone when it closes. The schedule's own entry
 * would keep every one it ever made; the arguments a route would carry come in [BookingArgs].
 */
@Composable
private fun rememberSheetViewModel(day: LocalDate): ClassEditViewModel {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return viewModel(
        viewModelStoreOwner = owner,
        factory = LocalMetroViewModelFactory.current,
        extras = MutableCreationExtras().apply { set(BookingArgs, mapOf("day" to day.toString())) },
    )
}

internal enum class QuickPicker { DATE, START, LENGTH, PRICE }

@Composable
private fun QuickBookBody(
    state: ClassFormState,
    viewModel: ClassEditViewModel,
    refusal: ClassEditViewModel.Event?,
    onBook: () -> Unit,
    onAllFields: () -> Unit,
) {
    val form = state.form
    val targets by viewModel.quickTargets.collectAsStateWithLifecycle()
    val durations by viewModel.durationOptions.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<QuickPicker?>(null) }

    TargetChips(
        targets = targets,
        chosen = form.assignee,
        state = state,
        onChoose = viewModel::choose,
    )
    GroupLine(state)

    SectionCard(Modifier.padding(horizontal = SheetGutter)) {
        Column(Modifier.padding(vertical = Spacing.inline)) {
            WhenRows(state, durations, viewModel::change, onPick = { picker = it })
            TermRows(state, viewModel::change, onPick = { picker = it })
        }
    }

    FilledFromLine(state)
    refusal?.let { event ->
        Text(
            refusalText(event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = SheetGutter),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline, Alignment.End),
    ) {
        TextButton(onClick = onAllFields) { Text(stringResource(R.string.class_quick_all_fields)) }
        Button(onClick = onBook, enabled = state.canSave) { Text(stringResource(R.string.class_quick_book)) }
    }

    QuickPickers(picker, state, viewModel, onDone = { picker = null })
}

@Composable
private fun QuickPickers(
    picker: QuickPicker?,
    state: ClassFormState,
    viewModel: ClassEditViewModel,
    onDone: () -> Unit,
) {
    when (picker) {
        QuickPicker.DATE -> DatePickerModal(
            initial = viewModel.datePickerDefault(),
            onConfirm = { date -> viewModel.change(BookingIntent.Edit { it.copy(date = date) }); onDone() },
            onDismiss = onDone,
        )
        QuickPicker.START -> TimePickerModal(
            initial = viewModel.startPickerDefault(),
            onConfirm = { time -> viewModel.change(BookingIntent.StartAt(time)); onDone() },
            onDismiss = onDone,
        )
        QuickPicker.LENGTH -> DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)); onDone() },
            onDismiss = onDone,
        )
        QuickPicker.PRICE -> {
            val label = stringResource(priceLabel(state.form.target))
            TextFieldDialog(
                title = label,
                label = label,
                initial = state.form.price.text,
                keyboardType = KeyboardType.Decimal,
                validate = { MoneyField(it).isValid },
                onConfirm = { text ->
                    viewModel.change(BookingIntent.Edit { it.copy(price = it.price.typed(text)) })
                },
                onDismiss = onDone,
            )
        }
        null -> Unit
    }
}

/**
 * Who the lesson is for, as one row of chips: the people taught most recently, then «All…» for anybody
 * else. Students and groups sit together — the full form's Student/Group switch is a step the sheet skips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetChips(
    targets: List<QuickTarget>,
    chosen: Assignee?,
    state: ClassFormState,
    onChoose: (Assignee) -> Unit,
) {
    FlowRow(
        Modifier.padding(horizontal = SheetGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        targets.forEach { target ->
            FilterChip(
                selected = target.who == chosen,
                onClick = { onChoose(target.who) },
                label = { Text(target.name) },
                leadingIcon = { TargetMark(target) },
            )
        }
        var everyone by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { everyone = true },
                label = { Text(stringResource(R.string.class_quick_everyone)) },
                leadingIcon = {
                    Icon(
                        KlasIcons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
            DropdownMenu(expanded = everyone, onDismissRequest = { everyone = false }) {
                val all = remember(state.students, state.groups) {
                    QuickTargets.pick(emptyList(), state.students, state.groups, shown = Int.MAX_VALUE)
                }
                MenuOptions(all, nameOf = { it.name }) { target ->
                    TickedMenuItem(
                        text = target.name,
                        selected = target.who == chosen,
                        onClick = { onChoose(target.who); everyone = false },
                        leadingIcon = {
                            Icon(
                                if (target.who is Assignee.Group) {
                                    KlasIcons.Outlined.Group
                                } else {
                                    KlasIcons.Outlined.Person
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetMark(target: QuickTarget) {
    when (val who = target.who) {
        is Assignee.Student -> Avatar(target.name, who.studentId, size = AvatarSize)
        is Assignee.Group -> Icon(
            KlasIcons.Outlined.Group,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
        )
    }
}

/** A group's roster, said rather than ticked: unticking somebody is the full form's. */
@Composable
private fun GroupLine(state: ClassFormState) {
    if (state.form.target != ClassTarget.GROUP || state.form.groupId == null) return
    val text = if (state.groupHasNoMembers) {
        stringResource(R.string.class_group_no_active_members)
    } else {
        state.members.filter { it.id !in state.form.excludedMemberIds }.joinToString { it.name }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.groupHasNoMembers) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = SheetGutter),
    )
}

/** Where the booking's values came from, so a pre-filled time reads as a suggestion, not a choice. */
@Composable
private fun FilledFromLine(state: ClassFormState) {
    val from = state.filledFrom ?: return
    Row(
        Modifier.padding(horizontal = SheetGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            KlasIcons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.class_quick_filled_from, from.formatDate()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AvatarSize = 24.dp
