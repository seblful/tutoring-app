package com.seblful.klas.ui.students

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.R
import com.seblful.klas.domain.billing.Statement
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.money
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.ExternalApps
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.LocalCurrency

/**
 * What the card sends about money: a reminder of what is owed, offered only while something is, and the
 * statement behind it. Both go through the share sheet, which is where Telegram, SMS and mail already are.
 */
internal class AccountShares(val remind: (() -> Unit)?, val statement: () -> Unit)

@Composable
internal fun rememberAccountShares(details: StudentDetails, apps: ExternalApps, failure: String): AccountShares {
    val resources by rememberUpdatedState(LocalResources.current)
    val currency = LocalCurrency.current
    val name = details.student?.name.orEmpty()
    val owed = (details.balance as? Balance.Owed)?.amount
    val share = { text: String ->
        apps.open(ExternalTarget.ShareText(text, resources.getString(R.string.share_with)), failure)
    }
    return AccountShares(
        remind = owed?.let { amount ->
            { share(resources.getString(R.string.reminder_text, name, amount.money(currency))) }
        },
        statement = { share(statementText(resources, currency, name, details)) },
    )
}

/** The statement as a message: a heading, a line per lesson and payment with the balance it left, the total. */
internal fun statementText(resources: Resources, currency: Currency, name: String, details: StudentDetails): String {
    val statement = Statement.of(details.classes.map { it.klass }, details.payments)
    val subjects = details.classes.associate { it.klass.id to it.subjectName }
    val lines = statement.entries.map { entry ->
        val (what, amount) = when (entry) {
            is Statement.Lesson ->
                resources.getString(R.string.statement_lesson, subjects[entry.klass.id].orEmpty()) to
                    entry.charge.money(currency)
            is Statement.Paid -> resources.getString(R.string.statement_payment) to
                "−" + entry.payment.amount.money(currency)
        }
        val after = entry.balanceAfter.money(currency)
        resources.getString(R.string.statement_line, entry.at.formatDate(), what, amount, after)
    }
    val total = when (val balance = statement.balance) {
        is Balance.Owed -> resources.getString(R.string.statement_owed, balance.amount.money(currency))
        is Balance.Credit -> resources.getString(R.string.statement_credit, balance.amount.money(currency))
        Balance.Settled -> resources.getString(R.string.statement_settled)
    }
    return (listOf(resources.getString(R.string.statement_title, name), "") + lines + listOf("", total))
        .joinToString("\n")
}
