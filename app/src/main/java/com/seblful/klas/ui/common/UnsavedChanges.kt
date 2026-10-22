@file:Suppress("ForbiddenImport") // The leave guard is the dialog family's.

package com.seblful.klas.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.seblful.klas.R
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.KlasButton

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

/** A form whose state can answer both halves of [leaveIntent]. */
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
 * The whole leave flow of a form screen — the system back gesture, the question, and what each
 * answer does — returning the request the toolbar's own back arrow should call.
 *
 * @param onSave what «Сохранить» means for this form — the same request as its save button, so a
 * save that owes a question of its own (billed drops) asks it from here too.
 */
@Composable
fun leaveGuard(form: LeavableForm, onLeave: () -> Unit, onSave: () -> Unit): () -> Unit {
    var leaving by remember { mutableStateOf<LeaveIntent?>(null) }
    val requestLeave = {
        when (val intent = leaveIntent(form)) {
            LeaveIntent.LEAVE -> onLeave()
            else -> leaving = intent
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = form.isDirty,
        onBackCompleted = requestLeave,
    )
    leaving?.let { intent ->
        UnsavedChangesDialog(
            intent = intent,
            onSave = { leaving = null; onSave() },
            onDiscard = { leaving = null; onLeave() },
            onKeepEditing = { leaving = null },
        )
    }
    return requestLeave
}

/**
 * Asks before unsaved work is lost. Show it for either asking [LeaveIntent]; [LeaveIntent.LEAVE]
 * means there was nothing to ask about and no dialog belongs on screen.
 *
 * @param onKeepEditing also what a back press or a tap outside does — staying is the safe answer, so
 * it is the one an accidental dismissal lands on.
 */
@Composable
private fun UnsavedChangesDialog(
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
                    if (canSave) R.string.unsaved_save_title else R.string.unsaved_discard_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (canSave) R.string.unsaved_save_text else R.string.unsaved_discard_text,
                ),
            )
        },
        confirmButton = {
            if (canSave) {
                KlasButton(onClick = onSave, kind = ButtonKind.Text) { Text(stringResource(R.string.action_save)) }
            } else {
                DiscardButton(onDiscard)
            }
        },
        dismissButton = {
            if (canSave) {
                DiscardButton(onDiscard)
            } else {
                KlasButton(onClick = onKeepEditing, kind = ButtonKind.Text) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun DiscardButton(onDiscard: () -> Unit) {
    KlasButton(onClick = onDiscard, kind = ButtonKind.Destructive) {
        Text(stringResource(R.string.unsaved_discard))
    }
}
