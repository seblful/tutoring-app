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

/** The bar's actions while lessons are picked: the two batch actions safe to offer (R4), each undone at once. */
@Composable
internal fun BatchActions(batch: LessonBatch) {
    NamedIconButton(KlasIcons.Outlined.CheckCircle, stringResource(R.string.batch_mark_taught), batch::markTaught)
    NamedIconButton(KlasIcons.Outlined.EventBusy, stringResource(R.string.batch_cancel), batch::cancel)
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
                    resources.getQuantityString(R.plurals.batch_cancelled, done.receipts.size, done.receipts.size),
                ) { batch.undo(done) }
                is LessonBatch.Done.ChangedSince -> messages.show(
                    resources.getQuantityString(R.plurals.batch_changed_since, done.count, done.count),
                )
                LessonBatch.Done.Failed -> messages.show(resources.getString(R.string.action_failed))
            }
        }
    }
}
