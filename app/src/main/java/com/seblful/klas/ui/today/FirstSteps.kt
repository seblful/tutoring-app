package com.seblful.klas.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.seblful.klas.domain.guide.FirstStep
import com.seblful.klas.domain.guide.PhoneSetup
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
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
    StepsCard(
        title = stringResource(if (steps.complete) Strings.first_steps_all_set else Strings.first_steps_title),
        complete = steps.complete,
        onHide = actions.hideFirstSteps,
        rows = FirstStep.entries.map { step ->
            Step(step.title, step.hint, done = step in steps.done) { actions.firstStep(step, card.student) }
        },
    )
}

/**
 * After a restore, what the backup could not bring because it is the phone's: reminders it lets through, a
 * calendar to mirror to, a folder for the weekly copy — only those the old phone had on. Ticked, like the first
 * steps, by the phone's own state.
 */
@Composable
internal fun PhoneSetupSection(setup: PhoneSetup, actions: TodayActions) {
    StepsCard(
        title = stringResource(if (setup.complete) Strings.phone_setup_done else Strings.phone_setup_title),
        complete = setup.complete,
        onHide = actions.hidePhoneSetup,
        rows = setup.steps.map { step ->
            Step(step.title, step.hint, done = step in setup.done) { actions.phoneStep(step) }
        },
    )
}

private class Step(val title: StringResource, val hint: StringResource, val done: Boolean, val onClick: () -> Unit)

/** A card of [rows] that tick themselves, counted in its header, put away by its one action. */
@Composable
private fun StepsCard(title: String, complete: Boolean, onHide: () -> Unit, rows: List<Step>) {
    HeaderedCard(
        title = title,
        summary = stringResource(Strings.first_steps_count, rows.count { it.done }, rows.size),
        action = SectionAction(
            stringResource(if (complete) Strings.first_steps_close else Strings.first_steps_hide),
            onClick = onHide,
        ),
    ) {
        val doneWord = stringResource(Strings.first_step_done)
        val toDoWord = stringResource(Strings.first_step_to_do)
        rows.forEach { row ->
            KlasRow(
                title = stringResource(row.title),
                supporting = if (row.done) null else stringResource(row.hint),
                start = if (row.done) {
                    RowStart.Icon(KlasIcons.Filled.CheckCircle, tint = klasColors.success)
                } else {
                    RowStart.Icon(KlasIcons.Outlined.Circle)
                },
                end = RowEnd.Opens.takeUnless { row.done },
                onClick = row.onClick,
                modifier = Modifier.semantics { stateDescription = if (row.done) doneWord else toDoWord },
            )
        }
    }
}

private val FirstStep.title: StringResource
    get() = when (this) {
        FirstStep.BOOK -> Strings.first_step_book
        FirstStep.MARK_TAUGHT -> Strings.first_step_mark
        FirstStep.PAYMENT -> Strings.first_step_payment
        FirstStep.HOMEWORK -> Strings.first_step_homework
        FirstStep.REMINDERS -> Strings.first_step_reminders
    }

private val FirstStep.hint: StringResource
    get() = when (this) {
        FirstStep.BOOK -> Strings.first_step_book_hint
        FirstStep.MARK_TAUGHT -> Strings.first_step_mark_hint
        FirstStep.PAYMENT -> Strings.first_step_payment_hint
        FirstStep.HOMEWORK -> Strings.first_step_homework_hint
        FirstStep.REMINDERS -> Strings.first_step_reminders_hint
    }

private val PhoneStep.title: StringResource
    get() = when (this) {
        PhoneStep.REMINDERS -> Strings.first_step_reminders
        PhoneStep.CALENDAR -> Strings.phone_step_calendar
        PhoneStep.WEEKLY_COPY -> Strings.backup_weekly_off
    }

private val PhoneStep.hint: StringResource
    get() = when (this) {
        PhoneStep.REMINDERS -> Strings.phone_step_reminders_hint
        PhoneStep.CALENDAR -> Strings.phone_step_calendar_hint
        PhoneStep.WEEKLY_COPY -> Strings.phone_step_weekly_hint
    }
