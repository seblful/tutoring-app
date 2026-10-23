package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toAmountInput
import com.seblful.klas.queries.students.SubjectPrice
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DialogRadioRow
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.RateHistory
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.paymentMethodLabel
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.FormDialog
import com.seblful.klas.ui.designsystem.component.KlasDivider
import kotlinx.datetime.LocalDate

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
    FormDialog(
        title = title,
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = { onConfirm(name.trim(), phone.trim(), email.trim(), note.trim(), mode); onDismiss() },
            enabled = name.isNotBlank(),
        ),
        onDismiss = onDismiss,
    ) {
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
    }
}

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
    FormDialog(
        title = subjectPrice.subject.name,
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = { field.amount?.let(onConfirm); onDismiss() },
            enabled = field.isValid,
        ),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
            MoneyTextField(
                field = field,
                onValueChange = { text = it },
                label = stringResource(R.string.price_label),
                modifier = Modifier.focusRequester(focus),
            )
            KlasDivider()
            RateHistory(subjectPrice.history)
        }
    }
}

@Composable
internal fun PaymentDialog(
    title: String,
    initialAmount: Money?,
    initialDate: LocalDate,
    initialMethod: PaymentMethod?,
    onConfirm: (Money, LocalDate, PaymentMethod?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Saveable, as every typed dialog field is: the text, with the field derived from it.
    var text by rememberSaveable { mutableStateOf(initialAmount?.toAmountInput().orEmpty()) }
    val field = MoneyField(text, MoneyField.Kind.PAYMENT)
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var method by rememberSaveable { mutableStateOf(initialMethod) }
    var picking by remember { mutableStateOf(false) }
    // A new payment opens to be typed into, as every other «Новый …» dialog does — the date is already
    // today. A correction does not: what is wrong with it may be the date.
    val focus = if (initialAmount == null) rememberRevealedFocus() else null

    FormDialog(
        title = title,
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = {
                // The day, not an instant: which hour a payment lands at is `PaymentDate`'s,
                // and it needs the injected clock this composable has no way to hold.
                field.amount?.let { onConfirm(it, date, method) }
                onDismiss()
            },
            enabled = field.isValid,
        ),
        onDismiss = onDismiss,
    ) {
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
            // Optional, so nothing need be selected: a tap on the chosen segment clears it.
            SegmentedChoice<PaymentMethod?>(
                options = PaymentMethod.entries,
                selected = method,
                onSelect = { method = it },
                onReselect = { method = null },
            ) { option -> option?.let { paymentMethodLabel(it) }.orEmpty() }
        }
    }

    if (picking) {
        DatePickerModal(
            initial = date,
            onConfirm = { date = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
internal fun AddSubjectPriceDialog(
    addableSubjects: List<Subject>,
    /** Whether the tutor has no subjects at all — which is not «every subject has been added». */
    noSubjectsYet: Boolean,
    onConfirm: (subjectId: Long, amount: Money) -> Unit,
    onDismiss: () -> Unit,
    initialSubjectId: Long? = null,
    title: String? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf(initialSubjectId) }
    var priceText by rememberSaveable { mutableStateOf("") }
    val price = MoneyField(priceText, MoneyField.Kind.RATE)
    FormDialog(
        title = title ?: stringResource(R.string.student_add_subject),
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = {
                val id = selectedId
                val amount = price.amount
                if (id != null && amount != null) { onConfirm(id, amount); onDismiss() }
            },
            enabled = selectedId != null && price.isValid,
        ),
        onDismiss = onDismiss,
    ) {
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
    }
}
