package com.seblful.klas.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.ui.common.Messages

/** Today's snackbars, each with the Undo its event offers. */
@Composable
internal fun TodayEvents(viewModel: TodayViewModel, messages: Messages) {
    val resources by rememberUpdatedState(currentTexts())
    LaunchedEffect(viewModel, messages) {
        viewModel.events.stream.collect { event ->
            when (event) {
                is TodayViewModel.Event.Marked -> messages.undoable(
                    resources.plural(Plurals.today_marked, event.changes.size, event.changes.size),
                ) { viewModel.undo(event) }
                TodayViewModel.Event.UndoChanged -> messages.show(resources.string(Strings.class_undo_changed))
                TodayViewModel.Event.ActionFailed -> messages.show(resources.string(Strings.action_failed))
                TodayViewModel.Event.FirstStepsHidden ->
                    messages.undoable(resources.string(Strings.first_steps_hidden)) { viewModel.showFirstSteps() }
                is TodayViewModel.Event.PhoneSetupHidden ->
                    messages.undoable(resources.string(Strings.phone_setup_hidden)) {
                        viewModel.showPhoneSetup(event.steps)
                    }
            }
        }
    }
}
