package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.domain.billing.Statement
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.money
import com.seblful.klas.print.Sheet
import com.seblful.klas.print.fileSafe
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ExternalApps
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.designsystem.component.ChoiceDialog
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowStart
import com.seblful.klas.ui.theme.KlasIcons

/**
 * What the card sends about money: a reminder of what is owed, offered only while something is, and the
 * statement behind it — as a message, or printed. All go through the share sheet, which is where Telegram,
 * SMS and mail already are.
 */
internal class AccountShares(val remind: (() -> Unit)?, val statement: () -> Unit, val statementPdf: () -> Unit)

/** [onPdf] is handed the statement printed: its filename, its subject line and the sheet. */
@Composable
internal fun rememberAccountShares(
    details: StudentDetails,
    apps: ExternalApps,
    failure: String,
    onPdf: (filename: String, subject: String, sheet: Sheet) -> Unit,
): AccountShares {
    val resources by rememberUpdatedState(currentTexts())
    val currency = LocalCurrency.current
    val name = details.student?.name.orEmpty()
    val owed = (details.balance as? Balance.Owed)?.amount
    val share = { text: String ->
        apps.open(ExternalTarget.ShareText(text, resources.string(Strings.share_with)), failure)
    }
    return AccountShares(
        remind = owed?.let { amount ->
            { share(resources.string(Strings.reminder_text, name, amount.money(currency))) }
        },
        statement = { share(statementText(resources, currency, name, details)) },
        statementPdf = {
            onPdf(
                fileSafe(resources.string(Strings.statement_filename, name)),
                resources.string(Strings.statement_title, name),
                statementSheet(resources, currency, name, details),
            )
        },
    )
}

/** One entry of the statement in words: when, what, how much, and the balance it left. */
private class StatementLine(val day: String, val what: String, val amount: String, val after: String)

/** The statement's entries in words, and its closing total. */
private fun statementLines(
    resources: Texts,
    currency: Currency,
    details: StudentDetails,
): Pair<List<StatementLine>, String> {
    val statement = Statement.of(details.classes.map { it.klass }, details.payments)
    val subjects = details.classes.associate { it.klass.id to it.subjectName }
    val lines = statement.entries.map { entry ->
        val (what, amount) = when (entry) {
            is Statement.Lesson ->
                resources.string(Strings.statement_lesson, subjects[entry.klass.id].orEmpty()) to
                    entry.charge.money(currency)
            is Statement.Paid -> resources.string(Strings.statement_payment) to
                "−" + entry.payment.amount.money(currency)
        }
        StatementLine(entry.at.formatDate(), what, amount, entry.balanceAfter.money(currency))
    }
    val total = when (val balance = statement.balance) {
        is Balance.Owed -> resources.string(Strings.statement_owed, balance.amount.money(currency))
        is Balance.Credit -> resources.string(Strings.statement_credit, balance.amount.money(currency))
        Balance.Settled -> resources.string(Strings.statement_settled)
    }
    return lines to total
}

/** The statement printed: a row per lesson and payment, the balance it left under it, the total at the end. */
fun statementSheet(resources: Texts, currency: Currency, name: String, details: StudentDetails): Sheet {
    val (lines, total) = statementLines(resources, currency, details)
    val rows = lines.map { line ->
        Sheet.Row(
            label = "${line.day} · ${line.what}",
            value = line.amount,
            detail = resources.string(Strings.statement_balance_after, line.after),
        )
    }
    return Sheet(
        title = resources.string(Strings.statement_title, name),
        sections = listOf(Sheet.Section("", rows)),
        closing = total,
    )
}

/** The statement as a message: a heading, a line per lesson and payment with the balance it left, the total. */
fun statementText(resources: Texts, currency: Currency, name: String, details: StudentDetails): String {
    val (lines, total) = statementLines(resources, currency, details)
    val texts = lines.map { resources.string(Strings.statement_line, it.day, it.what, it.amount, it.after) }
    return (listOf(resources.string(Strings.statement_title, name), "") + texts + listOf("", total))
        .joinToString("\n")
}

/** The statement's two forms, for the balance card's «Statement»: a message, or a PDF. */
@Composable
internal fun StatementChoice(shares: AccountShares, onDismiss: () -> Unit) {
    ChoiceDialog(title = stringResource(Strings.statement_send_title), onDismiss = onDismiss) {
        Column {
            KlasRow(
                stringResource(Strings.statement_as_message),
                start = RowStart.Icon(KlasIcons.Outlined.Send),
                onClick = { onDismiss(); shares.statement() },
            )
            KlasRow(
                stringResource(Strings.statement_as_pdf),
                start = RowStart.Icon(KlasIcons.Outlined.Assignment),
                onClick = { onDismiss(); shares.statementPdf() },
            )
        }
    }
}
