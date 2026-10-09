package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toAmountInput
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DialogRadioRow
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.RateHistory
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.common.text
import java.time.LocalDate

/** A dialog to edit a student's name, phone, email, optional note and default class mode. */
@Composable
fun StudentEditDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    initialNote: String,
    initialMode: ClassMode,
    onConfirm: (name: String, phone: String, email: String, note: String, mode: ClassMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // Saveable, so turning the phone keeps what was typed — a dialog that asks not to be tapped away
    // (`KeepTypedInput`) should not lose it to a rotation either.
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            // Five controls is the tallest dialog in the app, so it scrolls: with the keyboard up — or at
            // a large font scale — «Место» and the note fell off the bottom with no way to reach them,
            // an `AlertDialog`'s text slot having no scroll of its own.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.inline),
            ) {
                StudentContactFields(
                    name = name,
                    onName = { name = it },
                    phone = phone,
                    onPhone = { phone = it },
                    email = email,
                    onEmail = { email = it },
                    note = note,
                    onNote = { note = it },
                    mode = mode,
                    onMode = { mode = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), phone.trim(), email.trim(), note.trim(), mode); onDismiss() },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Changing one subject's rate, with what it has been before.
 *
 * The history belongs *here* rather than on the list: it is the context for the decision being made
 * — «было 25, стало 30» is the thing worth knowing while typing a new number — and on the list it was
 * a permanent strip answering a question nobody had yet asked.
 */
@Composable
internal fun RepriceDialog(
    subjectPrice: SubjectPrice,
    onConfirm: (Money) -> Unit,
    onDismiss: () -> Unit,
) {
    // The text, saveable, with the field derived from it: a half-typed rate survives the dialog being
    // recreated, which a `remember`ed field did not.
    var text by rememberSaveable { mutableStateOf(subjectPrice.price.toAmountInput()) }
    val field = MoneyField(text, MoneyField.Kind.RATE)
    // Opened to type one number, so it opens with the caret in it.
    val focus = rememberRevealedFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        // As every dialog the tutor types into: a stray tap outside must not discard the amount.
        properties = KeepTypedInput,
        title = { Text(subjectPrice.subject.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                MoneyTextField(
                    field = field,
                    onValueChange = { text = it },
                    label = stringResource(R.string.price_label),
                    modifier = Modifier.focusRequester(focus),
                )
                HorizontalDivider()
                RateHistory(subjectPrice.history)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { field.amount?.let(onConfirm); onDismiss() },
                enabled = field.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Recording or correcting one payment: how much, and **when**.
 *
 * The date is the addition. A payment used to be stamped with the moment it was typed, so a tutor
 * writing up the week on Sunday recorded five Sunday payments — the amounts were right and the
 * history was fiction. Defaulted to today, which is right most of the time.
 */
@Composable
internal fun PaymentDialog(
    title: String,
    initialAmount: Money?,
    initialDate: LocalDate,
    onConfirm: (Money, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Saveable, as every typed dialog field is: the text, with the field derived from it.
    var text by rememberSaveable { mutableStateOf(initialAmount?.toAmountInput().orEmpty()) }
    val field = MoneyField(text, MoneyField.Kind.PAYMENT)
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var picking by remember { mutableStateOf(false) }
    // A new payment opens to be typed into, as every other «Новый …» dialog does — the date is already
    // today. A correction does not: what is wrong with it may be the date.
    val focus = if (initialAmount == null) rememberRevealedFocus() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                MoneyTextField(
                    field = field,
                    onValueChange = { text = it },
                    label = stringResource(R.string.payment_amount_label),
                    modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
                )
                PickerField(
                    value = date.formatDate(),
                    label = stringResource(R.string.payment_date_label),
                    onClick = { picking = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The day, not an instant: which hour a payment lands at is `PaymentDate`'s,
                    // and it needs the injected clock this composable has no way to hold.
                    field.amount?.let { onConfirm(it, date) }
                    onDismiss()
                },
                enabled = field.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (picking) {
        DatePickerModal(
            initial = date,
            onConfirm = { date = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}
/**
 * Adds one subject to the student together with its price — the two are set in lock-step, so a
 * student's subjects are always exactly their priced (bookable) subjects. Offers only subjects the
 * student isn't already priced for; the save button stays disabled until a subject and a valid price
 * are both chosen.
 */
@Composable
internal fun AddSubjectPriceDialog(
    addableSubjects: List<Subject>,
    /** Whether the tutor has no subjects at all — which is not «every subject has been added». */
    noSubjectsYet: Boolean,
    onConfirm: (subjectId: Long, amount: Money) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var priceText by rememberSaveable { mutableStateOf("") }
    val price = MoneyField(priceText, MoneyField.Kind.RATE)
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap beside the dialog must not throw away the price just typed.
        properties = KeepTypedInput,
        title = { Text(stringResource(R.string.student_add_subject)) },
        text = {
            // Scrolls, as the student's edit dialog does: an `AlertDialog`'s text slot has no scroll of its
            // own, and with a handful of subjects the keyboard for the price covered the field being typed.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (addableSubjects.isEmpty()) {
                    Text(
                        stringResource(
                            if (noSubjectsYet) {
                                R.string.student_form_no_subjects
                            } else {
                                R.string.student_all_subjects_added
                            },
                        ),
                    )
                } else {
                    addableSubjects.forEach { subject ->
                        DialogRadioRow(
                            label = subject.name,
                            selected = selectedId == subject.id,
                            onClick = { selectedId = subject.id },
                        )
                    }
                    MoneyTextField(
                        field = price,
                        onValueChange = { priceText = it },
                        label = stringResource(R.string.price_label),
                        modifier = Modifier.padding(top = Spacing.inline),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = selectedId
                    val amount = price.amount
                    if (id != null && amount != null) { onConfirm(id, amount); onDismiss() }
                },
                enabled = selectedId != null && price.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
