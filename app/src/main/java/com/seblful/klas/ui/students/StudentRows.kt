package com.seblful.klas.ui.students

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.SubjectPrice
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.archivedMessage
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.lessonBadgeColor
import com.seblful.klas.ui.common.lessonBadgeLabel
import com.seblful.klas.ui.common.paymentMethodLabel
import com.seblful.klas.ui.common.rateChangeLine
import com.seblful.klas.ui.common.text
import kotlin.time.Instant

/*
 * The rows a student's lessons, payments and subjects are listed in — the same on their card, inside its
 * section cards, and on the page «See all» opens.
 */

/**
 * A lesson of theirs. Opens the lesson: a tutor reading a student's history is the one most likely to spot one
 * to correct.
 */
@Composable
internal fun LessonRow(
    row: ClassRow,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        headlineContent = {
            Text(
                // When first, as on the group card: it is what tells one row from the next, so it is the half
                // that must survive when a large font leaves room for only one of the two.
                bulletJoin(row.klass.start.dateTimeText(), row.subjectName),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val status = row.klass.status
            val phase = LessonPhase.of(row.klass, now)
            BadgeLine(badge = { StatusBadge(lessonBadgeLabel(status, phase), lessonBadgeColor(status, phase)) }) {
                // The group's name for a group lesson, since *which* group is the useful half of "not
                // individual" — a student in two groups is billed a different rate by each. An individual
                // lesson says nothing here: «Индивидуальное» repeated the icon on every row.
                row.groupName?.let { group ->
                    Text(
                        group,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            row.shownCharge?.let { TrailingAmount(it.text()) }
        },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) },
    )
}

/**
 * A payment, laid out as a lesson is: the day as its title, the money trailing (R1). Tapping it corrects it,
 * and deleting is in there too — a wrong amount is a typo far oftener than a payment that did not happen.
 */
@Composable
internal fun PaymentRow(
    payment: Payment,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        headlineContent = { Text(payment.date.formatDate()) },
        supportingContent = payment.method?.let { method -> { Text(paymentMethodLabel(method)) } },
        trailingContent = { TrailingAmount(payment.amount.text()) },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onEdit) },
    )
}

/**
 * A subject they take: its rate trailing, its last change under the name. Tapping it opens the rate, where
 * the subject is also taken off — one target instead of a pencil and a bin beside the number.
 */
@Composable
internal fun SubjectRow(
    subject: SubjectPrice,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val changed = rateChangeLine(subject.history)
    ListItem(
        headlineContent = { Text(subject.subject.name) },
        supportingContent = changed?.let { line ->
            { Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrailingAmount(stringResource(R.string.price_per_hour, subject.price.text()))
                OpensScreenMark()
            }
        },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onOpen) },
    )
}

/**
 * Says [event] in [this] — the card's words on every page of it, Undo included. The card says
 * [StudentDetailViewModel.Event.RemoveBlockedHasClasses] itself first, with the way to the lessons only it
 * can offer.
 */
internal fun Messages.showStudentEvent(
    event: StudentDetailViewModel.Event,
    resources: Resources,
    viewModel: StudentDetailViewModel,
) {
    when (event) {
        StudentDetailViewModel.Event.RemoveBlockedHasClasses ->
            show(resources.getString(R.string.student_subject_remove_blocked))
        is StudentDetailViewModel.Event.Archived ->
            undoable(resources.archivedMessage(event.archive.cancelledLessons)) { viewModel.undoArchive(event) }
        is StudentDetailViewModel.Event.ArchiveUndoKept ->
            show(resources.getQuantityString(R.plurals.archive_undo_kept, event.count, event.count))
        is StudentDetailViewModel.Event.PaymentCorrected ->
            undoable(resources.getString(R.string.payment_corrected)) { viewModel.undoPaymentCorrection(event) }
        StudentDetailViewModel.Event.PaymentChangedSince -> show(resources.getString(R.string.payment_undo_changed))
        StudentDetailViewModel.Event.WriteFailed -> show(resources.getString(R.string.action_failed))
        StudentDetailViewModel.Event.ShareFailed -> show(resources.getString(R.string.student_contact_failed))
    }
}
