package com.seblful.klas.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R

/**
 * What leaving a form should do — the rule behind the back arrow and the system back gesture.
 *
 * Leaving used to be unconditional: a filled-in form was thrown away without a word, which is the
 * one mistake a form can make that the tutor cannot undo. So back now asks. It asks *differently*
 * depending on whether there is anything to offer, and that is the whole of this decision: offering
 * «Сохранить» on a half-filled form would be an offer the save then refuses (every form's save is a
 * no-op while it is invalid), and asking anything at all about an untouched form would put a dialog
 * between the tutor and the back button for nothing.
 *
 * It lives here, outside the composables, for the reason the rest of the app's decisions do — a
 * `@Composable` can only be exercised on a device, and three screens have to agree on this.
 */
enum class LeaveIntent {

    /** Nothing has been typed: leaving is exactly what it looks like, so just leave. */
    LEAVE,

    /** Typed and complete — the two things worth doing with it are saving and discarding. */
    ASK_SAVE_OR_DISCARD,

    /**
     * Typed but not yet saveable (a name still missing, a price still blank). There is nothing to
     * save, so the only question left is whether to lose the work.
     */
    ASK_DISCARD,
}

/**
 * A form whose state can answer both halves of [leaveIntent].
 *
 * An interface rather than two boolean parameters, because two booleans is exactly how the three
 * screens came to disagree. `ClassEditScreen` passed its `canSave` — valid, no time conflict, not every
 * member unticked — while the two form screens passed `isValid`, which is only the first of those. Both
 * were right for their own screen, and nothing said so; the shared rule's own KDoc claimed one meaning
 * ("the same condition that enables its save button") that a reader had to check three call sites to
 * confirm. Naming it here makes each state say which of its properties is the save condition, once, and
 * leaves the call site nothing to get wrong.
 */
interface LeavableForm {

    /** Whether the form differs from what it was opened with. */
    val isDirty: Boolean

    /**
     * Whether the form is complete enough to be saved — **the condition that enables its save button**,
     * so the dialog can never offer a save that would silently do nothing.
     */
    val canSave: Boolean
}

/** What leaving [form] should do. */
fun leaveIntent(form: LeavableForm): LeaveIntent = when {
    !form.isDirty -> LeaveIntent.LEAVE
    form.canSave -> LeaveIntent.ASK_SAVE_OR_DISCARD
    else -> LeaveIntent.ASK_DISCARD
}

/**
 * Asks before unsaved work is lost. Show it for either asking [LeaveIntent]; [LeaveIntent.LEAVE]
 * means there was nothing to ask about and no dialog belongs on screen.
 *
 * @param onKeepEditing also what a back press or a tap outside does — staying is the safe answer, so
 *   it is the one an accidental dismissal lands on.
 */
@Composable
fun UnsavedChangesDialog(
    intent: LeaveIntent,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    val canSave = intent == LeaveIntent.ASK_SAVE_OR_DISCARD
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = {
            Text(
                stringResource(
                    if (canSave) R.string.unsaved_save_title else R.string.unsaved_discard_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (canSave) R.string.unsaved_save_text else R.string.unsaved_discard_text
                )
            )
        },
        confirmButton = {
            if (canSave) {
                TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
            } else {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard)) }
            }
        },
        dismissButton = {
            if (canSave) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard)) }
            } else {
                TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
