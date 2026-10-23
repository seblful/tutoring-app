package com.seblful.klas.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.seblful.klas.R
import com.seblful.klas.domain.guide.FirstStep
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.RowStart
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasColors

/**
 * A new tutor's first week, as five rows that tick themselves as each deed is done anywhere in the app — not
 * when the row is tapped. A row opens where its step is done; once all are, the card says so and waits to be
 * closed rather than vanishing under the tap that finished it.
 */
@Composable
internal fun FirstStepsSection(card: FirstStepsCard, actions: TodayActions) {
    val steps = card.steps
    HeaderedCard(
        title = stringResource(if (steps.complete) R.string.first_steps_all_set else R.string.first_steps_title),
        summary = stringResource(R.string.first_steps_count, steps.done.size, FirstStep.entries.size),
        action = SectionAction(
            stringResource(if (steps.complete) R.string.first_steps_close else R.string.first_steps_hide),
            actions.hideFirstSteps,
        ),
    ) {
        val doneWord = stringResource(R.string.first_step_done)
        val toDoWord = stringResource(R.string.first_step_to_do)
        FirstStep.entries.forEach { step ->
            val done = step in steps.done
            KlasRow(
                title = stringResource(step.title),
                supporting = if (done) null else stringResource(step.hint),
                start = if (done) {
                    RowStart.Icon(KlasIcons.Filled.CheckCircle, tint = klasColors.success)
                } else {
                    RowStart.Icon(KlasIcons.Outlined.Circle)
                },
                end = RowEnd.Opens.takeUnless { done },
                onClick = { actions.firstStep(step, card.student) },
                modifier = Modifier.semantics { stateDescription = if (done) doneWord else toDoWord },
            )
        }
    }
}

private val FirstStep.title: Int
    get() = when (this) {
        FirstStep.BOOK -> R.string.first_step_book
        FirstStep.MARK_TAUGHT -> R.string.first_step_mark
        FirstStep.PAYMENT -> R.string.first_step_payment
        FirstStep.HOMEWORK -> R.string.first_step_homework
        FirstStep.REMINDERS -> R.string.first_step_reminders
    }

private val FirstStep.hint: Int
    get() = when (this) {
        FirstStep.BOOK -> R.string.first_step_book_hint
        FirstStep.MARK_TAUGHT -> R.string.first_step_mark_hint
        FirstStep.PAYMENT -> R.string.first_step_payment_hint
        FirstStep.HOMEWORK -> R.string.first_step_homework_hint
        FirstStep.REMINDERS -> R.string.first_step_reminders_hint
    }
