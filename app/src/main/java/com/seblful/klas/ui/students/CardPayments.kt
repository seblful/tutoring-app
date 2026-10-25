package com.seblful.klas.ui.students

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.billing.PaymentSuggestions
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.ui.common.Messages
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal data class PaymentDefaults(
    val day: LocalDate,
    val method: PaymentMethod?,
    val suggestions: PaymentSuggestions,
)

/** One screen's payment interaction, shared by the student's overview and full payment list. */
internal class CardPayments(
    private val viewModel: StudentDetailViewModel,
    private val savedDialog: MutableState<PaymentDialogSnapshot>,
    private val deleteWithUndo: (Payment) -> Unit,
) {
    sealed interface Dialog {
        data class Adding(val defaults: PaymentDefaults) : Dialog
        data class Editing(val payment: Payment) : Dialog
    }

    var dialog: Dialog?
        get() = savedDialog.value.dialog()
        private set(value) { savedDialog.value = PaymentDialogSnapshot.of(value) }

    fun add() {
        dialog = viewModel.paymentDefaults()?.let { Dialog.Adding(it) }
    }

    fun open(payment: Payment) {
        dialog = Dialog.Editing(payment)
    }

    fun dismiss() {
        dialog = null
    }

    @Composable
    fun Dialogs() {
        val shown = dialog ?: return
        key(shown) {
            when (shown) {
                is Dialog.Adding -> PaymentDialog(
                    title = stringResource(R.string.payment_add_title),
                    initialAmount = null,
                    initialDate = shown.defaults.day,
                    initialMethod = shown.defaults.method,
                    suggestions = shown.defaults.suggestions,
                    onConfirm = viewModel::addPayment,
                    onDismiss = ::dismiss,
                )
                is Dialog.Editing -> {
                    val payment = shown.payment
                    PaymentDialog(
                        title = stringResource(R.string.payment_edit_title),
                        initialAmount = payment.amount,
                        initialDate = payment.date.toLocalDate(),
                        initialMethod = payment.method,
                        onConfirm = { amount, day, method -> viewModel.editPayment(payment, amount, day, method) },
                        onDelete = { deleteWithUndo(payment) },
                        onDismiss = ::dismiss,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberCardPayments(viewModel: StudentDetailViewModel, messages: Messages): CardPayments {
    val currentMessages by rememberUpdatedState(messages)
    val deleted by rememberUpdatedState(stringResource(R.string.payment_deleted))
    val dialog = rememberSerializable { mutableStateOf(PaymentDialogSnapshot()) }
    return remember(viewModel, dialog) {
        CardPayments(viewModel, dialog) { payment ->
            viewModel.deletePayment(payment)
            currentMessages.undoable(deleted) { viewModel.restorePayment(payment) }
        }
    }
}

@Serializable
internal data class PaymentDialogSnapshot(
    val open: Boolean = false,
    val id: Long? = null,
    val studentId: Long = 0,
    val day: LocalDate? = null,
    val at: Instant? = null,
    val amount: Long = 0,
    val method: PaymentMethod? = null,
    val balance: Long? = null,
    val lesson: Long? = null,
) {
    fun dialog(): CardPayments.Dialog? = when {
        !open -> null
        id != null -> CardPayments.Dialog.Editing(Payment(id, studentId, checkNotNull(at), Money(amount), method))
        else -> CardPayments.Dialog.Adding(
            PaymentDefaults(checkNotNull(day), method, PaymentSuggestions(balance?.let(::Money), lesson?.let(::Money))),
        )
    }

    companion object {
        fun of(dialog: CardPayments.Dialog?): PaymentDialogSnapshot = when (dialog) {
            null -> PaymentDialogSnapshot()
            is CardPayments.Dialog.Adding -> dialog.defaults.let {
                PaymentDialogSnapshot(
                    open = true,
                    day = it.day,
                    method = it.method,
                    balance = it.suggestions.fullBalance?.minor,
                    lesson = it.suggestions.oneLesson?.minor,
                )
            }
            is CardPayments.Dialog.Editing -> dialog.payment.let {
                PaymentDialogSnapshot(
                    open = true,
                    id = it.id,
                    studentId = it.studentId,
                    at = it.date,
                    amount = it.amount.minor,
                    method = it.method,
                )
            }
        }
    }
}
