package com.seblful.klas.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.R
import com.seblful.klas.ui.common.Messages

/** Today's snackbars, each with the Undo its event offers. */
@Composable
internal fun TodayEvents(viewModel: TodayViewModel, messages: Messages) {
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, messages) {
        viewModel.events.stream.collect { event ->
            when (event) {
                is TodayViewModel.Event.Marked -> messages.undoable(
                    resources.getQuantityString(R.plurals.today_marked, event.sessions.size, event.sessions.size),
                ) { viewModel.undo(event) }
                TodayViewModel.Event.UndoChanged -> messages.show(resources.getString(R.string.class_undo_changed))
                TodayViewModel.Event.ActionFailed -> messages.show(resources.getString(R.string.action_failed))
                TodayViewModel.Event.FirstStepsHidden ->
                    messages.undoable(resources.getString(R.string.first_steps_hidden)) { viewModel.showFirstSteps() }
                is TodayViewModel.Event.PhoneSetupHidden ->
                    messages.undoable(resources.getString(R.string.phone_setup_hidden)) {
                        viewModel.showPhoneSetup(event.steps)
                    }
            }
        }
    }
}
