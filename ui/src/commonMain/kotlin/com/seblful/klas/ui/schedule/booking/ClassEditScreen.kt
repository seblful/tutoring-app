package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.domain.pro.ProFeature
import com.seblful.klas.format.formatDate
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingFormScreen
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.template.FormScreen
import com.seblful.klas.ui.pro.LocalPro
import com.seblful.klas.ui.pro.ProDoor
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun ClassEditScreen(
    onBack: () -> Unit,
    viewModel: ClassEditViewModel = assistedMetroViewModel(),
    setup: BookingSetupNavigation = BookingSetupNavigation(),
) {
    LifecycleResumeEffect(Unit) {
        viewModel.refreshChoices()
        onPauseOrDispose { }
    }
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val title = stringResource(if (viewModel.isNew) Strings.class_add_title else Strings.class_edit_title)

    // Said, with the way back, rather than drawn as an empty form over a lesson that no longer exists.
    if (gone) {
        KlasScreen(title = title, onBack = onBack) { modifier, bottomInset ->
            EmptyState(
                title = stringResource(Strings.class_gone),
                detail = stringResource(Strings.class_gone_detail),
                icon = KlasIcons.Outlined.EventBusy,
                modifier = modifier.padding(bottomInset),
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
        is Loadable.Ready -> ClassEditForm(
            loaded.value, title, viewModel, onBack, setup,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassEditForm(
    state: ClassFormState,
    title: String,
    viewModel: ClassEditViewModel,
    onBack: () -> Unit,
    setup: BookingSetupNavigation,
) {
    val form = state.form
    val members = state.members

    // A save can still be refused after the button was enabled — say so rather than doing nothing.
    val messages = rememberMessages()
    val timeTakenMsg = stringResource(Strings.class_save_refused_time_taken)
    val noMembersMsg = stringResource(Strings.class_group_no_members_left)
    val allTakenMsg = stringResource(Strings.class_series_refused_all_taken)
    val saveFailedMsg = stringResource(Strings.save_failed)
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
    // can also do something about it. For the same reason a tutor who already refused once in Settings is
    // not asked again here: Android would have the app explain first, and a screen that is leaving cannot.
    val requestNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) {
        viewModel.askForNotifications.stream.collect {
            if (!requestNotifications.rationaleDue()) requestNotifications.launch()
        }
    }

    // A repeating booking that got some of its dates and not others — see [SeriesOutcome]. State rather
    // than a message, because a successful save navigates away and a snackbar on a screen being popped is
    // shown to nobody; the screen leaves when the dialog is dismissed.
    val seriesOutcome by viewModel.seriesOutcome.collectAsStateWithLifecycle()

    // Whether saving needs asking about, and how many billed lessons the question counts, are both
    // `state.saveIntent`'s — see [SaveIntent]. What is held here is only whether the dialog is up.
    var confirmingDrops by remember { mutableStateOf<SaveIntent.ConfirmBilledDrops?>(null) }
    val pro = LocalPro.current
    fun requestSave() {
        // A new group lesson is Klas Pro's; a group lesson already booked stays the tutor's to change.
        if (pro.refusesGroupBooking(viewModel.isNew, form.target)) {
            pro.open(ProFeature.GROUPS)
            return
        }
        when (val intent = state.saveIntent) {
            is SaveIntent.ConfirmBilledDrops -> confirmingDrops = intent
            SaveIntent.Save -> viewModel.save(onBack)
        }
    }

    // Back — the arrow or the system gesture — asks before throwing away a half-booked lesson.
    // Saving on the way out owes the same billed-drops warning as the save button, so the guard
    // answers «Сохранить» through the same request.
    val requestLeave = leaveGuard(form = state, onLeave = onBack, onSave = ::requestSave)

    // A taken time is as disqualifying as a missing field, and `canSave` already says so — the lesson could not
    // happen. The bar says which of the two it is.
    FormScreen(
        title = title,
        saveLabel = stringResource(if (viewModel.isNew) Strings.class_book_action else Strings.action_save),
        bar = bookingBar(state),
        onSave = ::requestSave,
        onLeave = requestLeave,
        messages = messages,
    ) { fields ->
        Column(
            fields.verticalScroll(rememberScrollState()).padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
        ) {
            FormSection(stringResource(Strings.class_section_who)) {
                WhoSection(state, viewModel.isNew, viewModel, setup)
            }
            FormSection(stringResource(Strings.class_section_when)) {
                WhenSection(state, viewModel)
            }
            DetailsSection(state, viewModel, viewModel.isNew)
        }
    }

    // Dismissing **is** leaving: the lessons are already written, so there is nothing to decide here and
    // only one button. What it must not do is let the form close on its own before the tutor has read
    // which dates were skipped — that is the whole reason the save held the navigation.
    seriesOutcome?.let { outcome ->
        val leave = { viewModel.acknowledgeSeries(); onBack() }
        NoticeDialog(
            text = seriesOutcomeText(outcome),
            onDismiss = leave,
            title = stringResource(Strings.class_series_title),
        )
    }

    confirmingDrops?.let { confirm ->
        ConfirmDialog(
            text = pluralStringResource(
                Plurals.class_member_drop_billed_confirm,
                confirm.billedLessons,
                confirm.billedLessons,
            ),
            confirmLabel = stringResource(Strings.action_save),
            onConfirm = { viewModel.save(onBack) },
            onDismiss = { confirmingDrops = null },
        )
    }
}

@Composable
private fun seriesOutcomeText(outcome: SeriesOutcome): String {
    val booked = pluralStringResource(Plurals.class_series_booked, outcome.booked, outcome.booked)
    val skipped = pluralStringResource(
        Plurals.class_series_skipped,
        outcome.skipped.size,
        outcome.skipped.size,
    )
    return "$booked\n\n$skipped ${outcome.skipped.joinToString { it.formatDate() }}"
}

@Composable
internal fun endsAtText(ends: ClassForm.Ending): String {
    val time = ends.time.timeText()
    return if (ends.nextDay) {
        stringResource(Strings.class_duration_ends_next_day, time)
    } else {
        stringResource(Strings.class_duration_ends_at, time)
    }
}

// PickerField and DropdownField live in ui/common/Fields.kt — every screen that hides a value
// behind a tap has to draw it the same way, which is exactly what this form was not doing.

/** Whether a new lesson for a group needs Klas Pro the tutor does not have. */
private fun ProDoor.refusesGroupBooking(isNew: Boolean, target: ClassTarget): Boolean =
    isNew && target == ClassTarget.GROUP && !allows(ProFeature.GROUPS)
