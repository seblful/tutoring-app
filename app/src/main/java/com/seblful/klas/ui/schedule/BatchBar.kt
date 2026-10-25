package com.seblful.klas.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/**
 * The bar's actions while lessons are picked: the batch actions safe to offer (R4), each undone at once. Cancel
 * is red, as the sheet's: it takes the lessons off the schedule and what the taught ones earned. When every
 * lesson picked is cancelled ([allCancelled]) its place is «Return to planned», in ink — green is the app's
 * «taught» — and Mark taught, which none of them can take, steps aside.
 */
@Composable
internal fun BatchActions(batch: LessonBatch, allCancelled: Boolean) {
    if (allCancelled) {
        NamedIconButton(KlasIcons.Filled.Undo, stringResource(R.string.class_correct_planned), batch::replan)
        return
    }
    NamedIconButton(KlasIcons.Outlined.CheckCircle, stringResource(R.string.batch_mark_taught), batch::markTaught)
    NamedIconButton(
        KlasIcons.Outlined.EventBusy,
        stringResource(R.string.batch_cancel),
        batch::cancel,
        tint = klasSemantics.destructive,
    )
}

/** What a batch did, said once in [messages] — with one Undo for the lot. */
@Composable
internal fun BatchMessages(batch: LessonBatch, messages: Messages) {
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(batch, messages) {
        batch.done.stream.collect { done ->
            when (done) {
                is LessonBatch.Done.Marked -> messages.undoable(
                    resources.getQuantityString(R.plurals.today_marked, done.sessions.size, done.sessions.size),
                ) { batch.undo(done) }
                is LessonBatch.Done.Cancelled -> messages.undoable(
                    resources.getQuantityString(R.plurals.batch_cancelled, done.changes.size, done.changes.size),
                ) { batch.undo(done) }
                is LessonBatch.Done.Replanned -> {
                    val clashed =
                        resources.getQuantityString(R.plurals.batch_replan_clashed, done.clashed, done.clashed)
                    if (done.changes.isEmpty()) {
                        messages.show(clashed)
                    } else {
                        val back = resources.getQuantityString(
                            R.plurals.batch_replanned,
                            done.changes.size,
                            done.changes.size,
                        )
                        messages.undoable(if (done.clashed > 0) "$back · $clashed" else back) { batch.undo(done) }
                    }
                }
                is LessonBatch.Done.ChangedSince -> messages.show(
                    resources.getQuantityString(R.plurals.batch_changed_since, done.count, done.count),
                )
                is LessonBatch.Done.Untouched -> messages.show(
                    resources.getString(
                        when (done.action) {
                            LessonBatch.Action.MARK_TAUGHT -> R.string.batch_none_marked
                            LessonBatch.Action.CANCEL -> R.string.batch_none_cancelled
                            LessonBatch.Action.REPLAN -> R.string.batch_none_replanned
                        },
                    ),
                )
                LessonBatch.Done.Failed -> messages.show(resources.getString(R.string.action_failed))
            }
        }
    }
}
