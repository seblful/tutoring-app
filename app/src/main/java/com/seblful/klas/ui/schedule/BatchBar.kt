package com.seblful.klas.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.stringResource
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
        NamedIconButton(KlasIcons.Filled.Undo, stringResource(Strings.class_correct_planned), batch::replan)
        return
    }
    NamedIconButton(KlasIcons.Outlined.CheckCircle, stringResource(Strings.batch_mark_taught), batch::markTaught)
    NamedIconButton(
        KlasIcons.Outlined.EventBusy,
        stringResource(Strings.batch_cancel),
        batch::cancel,
        tint = klasSemantics.destructive,
    )
}

/** What a batch did, said once in [messages] — with one Undo for the lot. */
@Composable
internal fun BatchMessages(batch: LessonBatch, messages: Messages) {
    val resources by rememberUpdatedState(currentTexts())
    LaunchedEffect(batch, messages) {
        batch.done.stream.collect { done ->
            when (done) {
                is LessonBatch.Done.Marked -> messages.undoable(
                    resources.plural(Plurals.today_marked, done.sessions.size, done.sessions.size),
                ) { batch.undo(done) }
                is LessonBatch.Done.Cancelled -> messages.undoable(
                    resources.plural(Plurals.batch_cancelled, done.changes.size, done.changes.size),
                ) { batch.undo(done) }
                is LessonBatch.Done.Replanned -> {
                    val clashed =
                        resources.plural(Plurals.batch_replan_clashed, done.clashed, done.clashed)
                    if (done.changes.isEmpty()) {
                        messages.show(clashed)
                    } else {
                        val back = resources.plural(
                            Plurals.batch_replanned,
                            done.changes.size,
                            done.changes.size,
                        )
                        messages.undoable(if (done.clashed > 0) "$back · $clashed" else back) { batch.undo(done) }
                    }
                }
                is LessonBatch.Done.ChangedSince -> messages.show(
                    resources.plural(Plurals.batch_changed_since, done.count, done.count),
                )
                is LessonBatch.Done.Untouched -> messages.show(
                    resources.string(
                        when (done.action) {
                            LessonBatch.Action.MARK_TAUGHT -> Strings.batch_none_marked
                            LessonBatch.Action.CANCEL -> Strings.batch_none_cancelled
                            LessonBatch.Action.REPLAN -> Strings.batch_none_replanned
                        },
                    ),
                )
                LessonBatch.Done.Failed -> messages.show(resources.string(Strings.action_failed))
            }
        }
    }
}
