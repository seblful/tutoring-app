package com.seblful.klas.ui.homework

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.seblful.klas.R
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.launchExternally

/** How a verdict reads — one word each, short enough for a third of a row of segments. */
fun homeworkResultLabel(result: HomeworkResult): Int = when (result) {
    HomeworkResult.DONE -> R.string.homework_result_done
    HomeworkResult.PARTIAL -> R.string.homework_result_partial
    HomeworkResult.NOT_DONE -> R.string.homework_result_not_done
}

/**
 * The three verdicts as segments, **with none selected until one is given** — the state every student's
 * homework is in until it is checked.
 *
 * [SegmentedChoice] over a nullable value rather than a fourth «не отмечено» segment: "not checked" is
 * what the row *is* before the tutor acts, not something they would pick, and offering it as a choice
 * would put an un-checking button on the busiest row of the tab. A wrong tap is corrected by tapping the
 * right verdict.
 */
@Composable
fun HomeworkResultSelector(
    result: HomeworkResult?,
    onResult: (HomeworkResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedChoice<HomeworkResult?>(
        options = HomeworkResult.entries,
        selected = result,
        onSelect = { picked -> picked?.let(onResult) },
        modifier = modifier,
        label = { option -> option?.let { stringResource(homeworkResultLabel(it)) }.orEmpty() },
    )
}

/**
 * A verdict per student the homework was set for. A group's members are each named above their own row
 * of segments — the title names the group, not them; an individual lesson's one student is the title,
 * so their row stands alone.
 */
@Composable
fun HomeworkVerdicts(
    row: HomeworkRow,
    onResult: (TutoringClass, HomeworkResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        row.members.forEach { member ->
            Column {
                if (row.isGroup) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = Spacing.tight),
                    )
                }
                HomeworkResultSelector(
                    result = member.klass.homeworkResult,
                    onResult = { result -> onResult(member.klass, result) },
                )
            }
        }
    }
}

/**
 * «Отправить»: the homework, worded for the student, into the phone's share sheet — a messenger, in
 * practice, which is where a tutor sends it.
 *
 * Through the share sheet rather than at the student's phone number: which messenger a family uses is
 * theirs, and the sheet already knows every one on the phone.
 */
@Composable
fun SendHomeworkButton(row: HomeworkRow, messages: Messages) {
    val context = LocalContext.current
    val text = homeworkShareText(row)
    val chooserTitle = stringResource(R.string.homework_send)
    val failed = stringResource(R.string.student_contact_failed)
    IconButton(onClick = { sendHomework(context, text, chooserTitle, failed, messages) }) {
        Icon(Icons.Filled.Share, contentDescription = chooserTitle)
    }
}

/**
 * The homework as the student reads it: with the day it is for when a lesson is booked to bring it to,
 * and on its own when none is yet. A date and not a time, since the time is the lesson's and they know it.
 */
@Composable
fun homeworkShareText(row: HomeworkRow): String =
    row.dueAt
        ?.let { stringResource(R.string.homework_share_due, it.formatDate(), row.text) }
        ?: stringResource(R.string.homework_share, row.text)

/**
 * Setting or correcting a lesson's homework without opening its whole form — the sheet's way in, since
 * «§ 12, упр. 4–7» is typed walking out of the room, and the form's twelve fields are not what the tutor
 * came for. Saving blank removes it.
 */
@Composable
fun HomeworkDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap beside the dialog must not throw away what was just typed.
        properties = KeepTypedInput,
        title = { Text(stringResource(R.string.homework_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = HomeworkDialogLines,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** An assignment and a line of what to watch for, before the field starts to grow. */
private const val HomeworkDialogLines = 3

private fun sendHomework(context: Context, text: String, chooserTitle: String, failed: String, messages: Messages) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    launchExternally(context, Intent.createChooser(send, chooserTitle), failed, messages)
}
