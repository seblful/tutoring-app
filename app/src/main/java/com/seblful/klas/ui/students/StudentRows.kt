package com.seblful.klas.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.SubjectPrice
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.homework.HomeworkUndo
import com.seblful.klas.ui.homework.homeworkUndo
import com.seblful.klas.ui.theme.KlasIcons

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        leadingContent = { LessonKindIcon(row.kind) },
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
            BadgeLine(badge = { StatusBadge(classStatusLabel(status), classStatusColor(status)) }) {
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
        trailingContent = { row.shownCharge?.let { TrailingAmount(it.text()) } },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) },
    )
}

/**
 * A payment. Tapping it corrects it; the bin removes it. Correcting is the commoner of the two — a wrong
 * amount is a typo, not a payment that did not happen.
 */
@Composable
internal fun PaymentRow(
    payment: Payment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        headlineContent = { Text(payment.amount.text()) },
        supportingContent = { Text(payment.date.formatDate()) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    KlasIcons.Outlined.Delete,
                    stringResource(R.string.payment_delete, payment.amount.text(), payment.date.formatDate()),
                )
            }
        },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onEdit) },
    )
}

/** A subject they take, at its price, re-priced or taken off from the row. */
@Composable
internal fun SubjectRow(
    subject: SubjectPrice,
    onReprice: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        headlineContent = { Text(subject.subject.name) },
        supportingContent = {
            Text(
                stringResource(R.string.price_per_hour, subject.price.text()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onReprice) {
                    Icon(KlasIcons.Outlined.Edit, stringResource(R.string.price_edit_title))
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        KlasIcons.Outlined.Delete,
                        stringResource(R.string.student_subject_remove, subject.subject.name),
                    )
                }
            }
        },
        colors = colors,
        modifier = modifier,
    )
}

/** Adding a payment, or correcting [editing]; nothing while neither is asked for. */
@Composable
internal fun PaymentDialogs(
    viewModel: StudentDetailViewModel,
    adding: Boolean,
    editing: Payment?,
    onDismiss: () -> Unit,
) {
    if (adding) {
        PaymentDialog(
            title = stringResource(R.string.payment_add_title),
            initialAmount = null,
            initialDate = viewModel.paymentDay(),
            onConfirm = { amount, date -> viewModel.addPayment(amount, date) },
            onDismiss = onDismiss,
        )
    }
    editing?.let { payment ->
        PaymentDialog(
            title = stringResource(R.string.payment_edit_title),
            initialAmount = payment.amount,
            initialDate = payment.date.toLocalDate(),
            onConfirm = { amount, date -> viewModel.editPayment(payment, amount, date) },
            onDismiss = onDismiss,
        )
    }
}

/** Their homework's swipes and deletions, each offered back in [messages]. */
@Composable
internal fun studentHomeworkUndo(viewModel: StudentDetailViewModel, messages: Messages): HomeworkUndo = homeworkUndo(
    messages = messages,
    setDone = viewModel::setHomeworkDone,
    putBack = viewModel::putHomeworkBack,
    delete = viewModel::deleteHomework,
    restore = viewModel::restoreHomework,
)

/** Deletes a payment and offers it back, in [messages]. */
@Composable
internal fun paymentDeleteWithUndo(viewModel: StudentDetailViewModel, messages: Messages): (Payment) -> Unit {
    val deleted = stringResource(R.string.payment_deleted)
    val undo = stringResource(R.string.action_undo)
    return { payment ->
        viewModel.deletePayment(payment)
        messages.show(deleted, undo) { viewModel.restorePayment(payment) }
    }
}
